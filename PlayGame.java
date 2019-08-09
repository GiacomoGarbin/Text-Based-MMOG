import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PlayGame extends Thread {
    // attributes

    private User user;
    private Game game;

    // IPC
    private MessageQueue iQueue;
    private MessageQueue oQueue;
    private Server server;
    private Client client;
    private MessageHandler handler;
    private HashMap<String, Acknowledgment> ackMap;

    // token ring
    private User prev;
    private User next;
    private Semaphore mutex;
    private TokenRing ring;
    private Semaphore ringEntryRequest;
    private boolean ringEntrySucceeded;

    private Position position;
    private PositionQueue positionQueue;

    private int score;

    private User eaten;

    private BombBag bombBag;
    private BombGenerator bombGenerator;
    private BombQueue bombQueue;
    private BombLauncher bombLauncher;
    private ArrayList<User> bombHits;

    private boolean gameOver;

    // getters & setters

    public User getUser() { return user; }
    public Game getGame() { return game; }

    public User getPrev() { return prev; }
    public void setPrev(User user) { prev = user; }
    public User getNext() { return next; }
    public void setNext(User user) { next = user; }

    public MessageQueue getOQueue() { return oQueue; }
    public MessageQueue getIQueue() { return iQueue; }

    public Server getServer() { return server; }
    public Client getClient() { return client; }
    public MessageHandler getHandler() { return handler; }
    public HashMap<String, Acknowledgment> getAckMap() { return ackMap; }

    public Semaphore getMutex() { return mutex; }
    public TokenRing getRing() { return ring; }

    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }

    public PositionQueue getPositionQueue() { return positionQueue; }
    public void setPositionQueue(PositionQueue queue) { positionQueue = queue; }

    public Semaphore getRingEntryRequest() { return ringEntryRequest; }
    public void setRingEntrySucceeded(boolean flag) { ringEntrySucceeded = flag; }

    public synchronized boolean getGameOver() { return gameOver; }
    public synchronized void setGameOver(boolean flag) { gameOver = flag; }

    public int getScore() { return score; }
    public void setScore(int value) { score = value; }

    public void setEaten(User user) { eaten = user; }

    public BombQueue getBombQueue() { return bombQueue; }
    public BombGenerator getBombGenerator() { return bombGenerator; }
    public BombLauncher getBombLauncher() { return bombLauncher; }
    public ArrayList<User> getBombHits() { return bombHits; }

    // methods

    private boolean entry() {
        if (game.getUsers().isEmpty()) {
            prev = user;
            next = user;
            ring = new TokenRing(true, this);

            mutex.acquire();
            ring.requestCS();

            MessageType result = SRC.addUser(game.getName(), user);

            if (result != MessageType.OK) {
                switch (result) {
                    case GAME_NOT_EXIST:
                        // ...
                        break;
                    case GAME_USER_DUPLICATION:
                        // ...
                        break;
                }

                ring.releaseCS();
                mutex.release();

                System.out.println("... sorry, something went wrong at the entry of the game.");
                return false;
            }

            // set user start position
            position.setRandomPosition();

            ring.releaseCS();
            mutex.release();
        } else {

            // updates the selected game information

            Message response = SRC.viewGame(game.getName());

            if (response.getType() == MessageType.GAME_NOT_EXIST) {
                System.out.println("Sorry, you have selected a non-existent or finished game.");
                return false;
            }

            game = (Game) Json.fromJson(response.getBody(), Game.class);

            // check GAME_USER_DUPLICATION :: client side

            for (User tmp : game.getUsers()) {
                if (tmp.getName().equals(user.getName())) {
                    System.out.println("Sorry, the name you choose has already been used by another player in this game.");
                    return false;
                }
            }

            ring = new TokenRing(false, this);
            ringEntryRequest = new Semaphore(1, 0);

            // try to put a delay here and see what happens
            /*
            try {
                Thread.sleep(1000 * (user.getPort() % 2000));
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
            */

            IPC.sendMessage(this, game.getUsers(), MessageType.RING_ENTRY_REQUEST, null, null, false);

            // waiting to enter ...
            try {
                ringEntryRequest.acquire(5000);
            } catch (TimeoutException exc) {
                System.out.println("... sorry, something went wrong at the entry of the game.");
                client.exit();
                return false;
            }

            if (!ringEntrySucceeded) {
                return false;
            }
        }

        return true;
    }

    public boolean exit(boolean inCS) {
        if (!inCS) {
            mutex.acquire();
            ring.requestCS();
        }

        // update user list :: server side

        MessageType result = SRC.removeUser(game.getName(), user.getName());

        if (result != MessageType.OK) {
            switch (result) {
                case GAME_NOT_EXIST:
                    System.out.println("Sorry, you have selected a non-existent or finished game.");
                    break;
            }
            if (!inCS) {
                ring.releaseCS();
                mutex.release();
            }
            return false;
        }

        if (!game.getUsers().isEmpty()) {
            // update neighbors
            IPC.sendMessage(this, prev, MessageType.NEIGHBORS_UPDATE, null, Json.toJson(new Neighbors(null, next)), true);
            IPC.sendMessage(this, next, MessageType.NEIGHBORS_UPDATE, null, Json.toJson(new Neighbors(prev, null)), true);

            // update user list :: client side
            IPC.sendMessage(this, game.getUsers(), MessageType.USER_LIST_UPDATE, null, Json.toJson(game.getUsers()), true);

            // closure socket
            IPC.sendMessage(this, game.getUsers(), MessageType.SOCKET_CLOSURE, null, null, false);
        }

        if (!inCS) {
            ring.releaseCS();
            mutex.release();
        }
        return true;
    }

    public void closeIPC() {
        server.exit();
        client.exit();
        handler.exit();

        try {
            server.join();
            client.join();
            handler.join();
        } catch (InterruptedException exc) {
            exc.printStackTrace();
        }
    }

    private void quit() throws QuitGameException {
        if (bombLauncher.stillBomb()) {
            System.out.println("Sorry, you can't leave the game until all the bombs you have launched have exploded.");
        } else {
            bombGenerator.exit();
            bombLauncher.exit();

            IPC.sendMessage(this, game.getUsers(), MessageType.GAME_USER_LEFT, null, null, true);

            exit(false);

            System.out.println(" *** GAME OVER ***");

            ring.unlock();
            client.exit();

            throw new QuitGameException();
        }
    }

    private void updatePosition(String input) {
        mutex.acquire();
        ring.requestCS();

        if (gameOver) {
            // ring.releaseCS();
            mutex.release();
            return;
        }

        // WASD keys
        switch (input) {
            case "w":
            case "W":
                position.decY();
                break;
            case "a":
            case "A":
                position.decX();
                break;
            case "s":
            case "S":
                position.incY();
                break;
            case "d":
            case "D":
                position.incX();
                break;
        }

        System.out.println("Waiting to communicate your move...");
        IPC.sendMessage(this, game.getUsers(), MessageType.POSITION_CHECK, null, Json.toJson(position), true);
        System.out.println("...your move has been communicated.");

        if (eaten != null) {
            score++;
            System.out.format("You ate %s and your score was increased by 1! :)\n", eaten.getName());

            // closure last socket
            AddressPort key = new AddressPort(eaten.getAddress(), eaten.getPort());
            client.closeSocket(key);

            eaten = null;
        }

        if (score >= game.getScore()) {
            gameOver = true;

            bombGenerator.exit();
            bombLauncher.exit();

            // sending GAME_OVER messages must be serialized

            HashMap<String, String> head = new HashMap<String, String>();
            head.put("output", String.format("Oh no, %s reached the target score and won the game! :(", user.getName()));

            User[] users = game.getUsers().toArray(new User[game.getUsers().size()]);
            for (User user : game.getUsers()) {
                IPC.sendMessage(this, user, MessageType.GAME_OVER, head, null, true);
            }

            System.out.format("Congratulations you reached the %d points target score and won the game! :D\n", game.getScore());
            System.out.println(" *** GAME OVER ***\nPlease press ENTER to continue.");

            exit(true);
        }

        if (!getGameOver()) {
            System.out.format("Your new position is: %s.\n", position);
            System.out.println("Where do you want to move? (W = up, A = left, S = down, D = right)");
        }

        ring.releaseCS();
        mutex.release();

        if (gameOver) {
            ring.unlock();
            client.exit();
            handler.exit();
            // closeIPC();
        }
    }

    private void launchBomb() {
        mutex.acquire();
        ring.requestCS();

        if (gameOver) {
            // ring.releaseCS();
            mutex.release();
            return;
        }

        Bomb bomb = bombBag.pop();

        if (bomb == null) {
            System.out.println("Sorry, your bomb bag is empty. :/");
        } else {
            System.out.format("Waiting to trigger a %s bomb...\n", bomb.getArea().toString().toLowerCase());
            IPC.sendMessage(this, game.getUsers(), MessageType.BOMB_LAUNCH, null, Json.toJson(bomb), true);
            System.out.println("...the bomb was launched and it will explode in 5 seconds!");

            bombQueue.push(bomb);
        }

        ring.releaseCS();
        mutex.release();
    }

    // keyboard input

    private BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
    private String getInput() {
        String input = null;
        try {
            input = bufferedReader.readLine();
        } catch (IOException exc) {
            exc.printStackTrace();
        }
        return input;
    }

    // constructor & run

    public PlayGame(User user, Game game) {
        this.user = user;
        this.game = game;

        iQueue = new MessageQueue();
        oQueue = new MessageQueue();

        server = new Server(user.getPort(), iQueue);
        client = new Client(oQueue);
        handler = new MessageHandler(this);
        ackMap = new HashMap<String, Acknowledgment>();

        mutex = new Semaphore(1);

        position = new Position(game.getGrid());

        score = 0;

        eaten = null;

        double alpha = 0.5;
        double threshold = 10.0;
        bombBag = new BombBag();
        bombGenerator = new BombGenerator(bombBag, alpha, threshold);
        bombQueue = new BombQueue();
        bombLauncher = new BombLauncher(this);
        bombHits = new ArrayList<User>();

        start();
    }

    public void run() {
        System.out.println("Please wait for the entry in the game...");

        if (!entry()) {
            closeIPC();
        } else {
            System.out.println("...you're in the game, good luck! ;) (To quit type Q.)");

            CheckFunction<String> checkInput = (input) -> {
                Pattern pattern;
                Matcher matcher;

                pattern = Pattern.compile("q|Q|w|W|a|A|s|S|d|D|b|B");
                matcher = pattern.matcher(input);

                if (!matcher.matches()) {
                    System.out.println("Sorry, you typed an invalid character.");
                    return false;
                }

                return true;
            };

            String input;

            System.out.format("Your start position is: %s.\n", position);
            System.out.println("Where do you want to move? (W = up, A = left, S = down, D = right)");

            bombGenerator.start();
            bombLauncher.start();

            try {
                while (true) {
                    do {
                        input = getInput();
                    } while (!gameOver && !checkInput.check(input));

                    if (!gameOver) {
                        switch (input) {
                            case "q":
                            case "Q":
                                quit();
                                break;

                            case "w":
                            case "W":
                            case "a":
                            case "A":
                            case "s":
                            case "S":
                            case "d":
                            case "D":
                                updatePosition(input);
                                break;

                            case "b":
                            case "B":
                                launchBomb();
                                break;
                        }
                    } else {
                        throw new QuitGameException();
                    }
                }
            } catch (QuitGameException exc) {
                closeIPC();
            }
        }
    }
}