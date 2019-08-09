import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

class MessageHandler extends Thread {
    PlayGame play;

    public MessageHandler(PlayGame play) {
        this.play = play;

        start();
    }

    public void run() {
        ArrayList<MessageHandlerChild> children = new ArrayList<MessageHandlerChild>();
        // ArrayList<MessageType> types = new ArrayList<MessageType>();

        while (true) {
            // clean up
            Iterator<MessageHandlerChild> iter = children.iterator();

            while (iter.hasNext()) {
                MessageHandlerChild child = iter.next();

                if (child.getState() == Thread.State.TERMINATED) {
                    iter.remove();
                }
            }

            Message message = play.getIQueue().pop();

            if (message == null) {
                break;
            }

            /*
            if (play.getGameOver() && !types.contains(message.getType())) {
                // continue;
            }
            */

            children.add(new MessageHandlerChild(play, message));
        }

        for (MessageHandlerChild child : children) {
            try {
                child.join();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
    }

    public void exit() {
        play.getIQueue().push(null);
    }
}

class MessageHandlerChild extends Thread {
    private PlayGame play;
    private Message message;

    public MessageHandlerChild(PlayGame play, Message message) {
        this.play = play;
        this.message = message;

        start();
    }

    public void run() {
        HashMap<String, String> head = new HashMap<String, String>();
        Position position;
        Bomb bomb;
        Neighbors neighbors;

        switch (message.getType()) {

            case TOKEN:
                play.getRing().handleMessage(message);
                break;

            case ACKNOWLEDGED:
                String ackIdx = message.getHead().get("ackIdx");
                play.getAckMap().get(ackIdx).releaseAck();

                if (play.getAckMap().get(ackIdx).isReleased()) {
                    play.getAckMap().remove(ackIdx);
                }
                break;

            case RING_ENTRY_REQUEST:
                play.getMutex().acquire();
                play.getRing().requestCS();

                if (play.getGameOver()) {
                    // ring.releaseCS();
                    play.getMutex().release();
                    return;
                }

                try {
                    if (!play.getGame().getUsers().contains(message.getSender())) {
                        // try to put a delay here and see what happens
                        /*
                        try {
                            Thread.sleep(1000 * (message.getSender().getPort() % 2000));
                        } catch (InterruptedException exc) {
                            exc.printStackTrace();
                        }
                        */

                        // make sure the user is still waiting to enter, this will launch a ConnectException if the user has already closed his ServerSocket
                        (new Socket(message.getSender().getAddress(), message.getSender().getPort())).close();

                        // update user list :: server side

                        MessageType result = SRC.addUser(play.getGame().getName(), message.getSender());

                        if (result != MessageType.OK) {
                            switch (result) {
                                case GAME_NOT_EXIST:
                                    // ...
                                    break;
                                case GAME_USER_DUPLICATION:
                                    // ...
                                    break;
                            }
                            IPC.sendMessage(play, message.getSender(), MessageType.RING_ENTRY_FAILED, null, null, false);
                        } else {
                            // set user start position

                            play.setPositionQueue(new PositionQueue(1 + play.getGame().getUsers().size()));
                            play.getPositionQueue().push(play.getPosition());

                            IPC.sendMessage(play, play.getGame().getUsers(), MessageType.POSITION_REQUEST, null, null, false);

                            // waiting for positions...
                            Position[] positions = play.getPositionQueue().pop();

                            CheckFunction<Position> checkPosition = (obj) -> {
                                for (Position tmp : positions) {
                                    if (tmp.equals(obj)) {
                                        return false;
                                    }
                                }
                                return true;
                            };

                            position = new Position(play.getGame().getGrid());

                            do {
                                position.setRandomPosition();
                            } while (!checkPosition.check(position));

                            IPC.sendMessage(play, message.getSender(), MessageType.POSITION_UPDATE, null, Json.toJson(position), true);

                            // update neighbors

                            IPC.sendMessage(play, message.getSender(), MessageType.NEIGHBORS_UPDATE, null, Json.toJson(new Neighbors(play.getPrev(), play.getUser())), true);

                            if (play.getUser().equals(play.getPrev())) {
                                play.setNext(message.getSender());
                            } else {
                                IPC.sendMessage(play, play.getPrev(), MessageType.NEIGHBORS_UPDATE, null, Json.toJson(new Neighbors(null, message.getSender())), true);
                            }

                            play.setPrev(message.getSender());

                            // update user list :: client side

                            play.getGame().getUsers().add(message.getSender());
                            play.getGame().getUsers().add(play.getUser());

                            IPC.sendMessage(play, play.getGame().getUsers(), MessageType.USER_LIST_UPDATE, null, Json.toJson(play.getGame().getUsers()), true);

                            play.getGame().getUsers().remove(play.getUser());

                            // ring entry succeeded

                            IPC.sendMessage(play, message.getSender(), MessageType.RING_ENTRY_SUCCEEDED, null, null, false);
                        }

                    }
                } catch (ConnectException exc) {
                    // exc.printStackTrace();
                } catch (IOException exc) {
                    exc.printStackTrace();
                }

                play.getRing().releaseCS();
                play.getMutex().release();
                break;

            case RING_ENTRY_FAILED:
                play.setRingEntrySucceeded(false);
                play.getRingEntryRequest().release();
                break;

            case RING_ENTRY_SUCCEEDED:
                play.setRingEntrySucceeded(true);
                play.getRingEntryRequest().release();

                IPC.sendMessage(play, play.getGame().getUsers(), MessageType.GAME_USER_WELCOME, null, null, true);
                break;

            case GAME_USER_WELCOME:
                System.out.format("A new player entered the game: welcome to %s!\n", message.getSender().getName());

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case GAME_USER_LEFT:
                System.out.format("%s left the game.\n", message.getSender().getName());

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case NEIGHBORS_UPDATE:
                if (message.getBody() != null) {
                    neighbors = (Neighbors) Json.fromJson(message.getBody(), Neighbors.class);

                    if (neighbors.getPrev() != null) {
                        play.setPrev(neighbors.getPrev());
                    }
                    if (neighbors.getNext() != null) {
                        play.setNext(neighbors.getNext());
                    }
                } else {
                    IPC.sendMessage(play, play.getPrev(), MessageType.NEIGHBORS_UPDATE, null, Json.toJson(new Neighbors(null, play.getNext())), true);
                    IPC.sendMessage(play, play.getNext(), MessageType.NEIGHBORS_UPDATE, null, Json.toJson(new Neighbors(play.getPrev(), null)), true);
                }

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case USER_LIST_UPDATE:
                if (message.getBody() != null) {
                    User[] users = (User[]) Json.fromJson(message.getBody(), User[].class);

                    play.getGame().setUsers(new ArrayList<User>(Arrays.asList(users)));
                    play.getGame().getUsers().remove(play.getUser());
                } else {
                    IPC.sendMessage(play, play.getGame().getUsers(), MessageType.USER_LIST_UPDATE, null, Json.toJson(play.getGame().getUsers()), true);
                }

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case POSITION_REQUEST:
                IPC.sendMessage(play, message.getSender(), MessageType.POSITION_VALUE, null, Json.toJson(play.getPosition()), false);
                break;

            case POSITION_VALUE:
                position = (Position) Json.fromJson(message.getBody(), Position.class);
                play.getPositionQueue().push(position);
                break;

            case POSITION_UPDATE:
                play.setPosition((Position) Json.fromJson(message.getBody(), Position.class));

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case POSITION_CHECK:
                position = (Position) Json.fromJson(message.getBody(), Position.class);

                if (position.equals(play.getPosition())) {
                    play.setGameOver(true);

                    play.getBombGenerator().exit();
                    play.getBombLauncher().exit();
                    play.exit(true);

                    System.out.format("Oh no, you've been eaten by %s! :(\n", message.getSender().getName());
                    System.out.println(" *** GAME OVER ***\nPlease press ENTER to continue.");

                    IPC.sendMessage(play, message.getSender(), MessageType.POSITION_MATCH, head, null, true);
                }

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);

                if (play.getGameOver()) {
                    play.getRing().unlock();
                    play.getClient().exit();
                    play.getHandler().exit();
                    // play.closeIPC();
                }
                break;

            case POSITION_MATCH:
                play.setEaten(message.getSender());

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case BOMB_LAUNCH:
                if (!play.getGameOver()) {
                    bomb = (Bomb) Json.fromJson(message.getBody(), Bomb.class);
                    System.out.format("%s launched a %s bomb, it will explode in 5 seconds!\n", message.getSender().getName(), bomb.getArea().toString().toLowerCase());
                }

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case BOMB_EXPLOSION:
                if (!play.getGameOver()) {
                    bomb = (Bomb) Json.fromJson(message.getBody(), Bomb.class);
                    System.out.format("KABOOM! The %s bomb launched by %s exploded!\n", bomb.getArea().toString().toLowerCase(), message.getSender().getName());

                    if (play.getPosition().getArea() == bomb.getArea()) {
                        play.setGameOver(true);

                        play.getBombGenerator().exit();
                        play.getBombLauncher().exit();
                        play.exit(true);

                        System.out.format("Oh no, you've been hit by the %s bomb launched by %s! :(\n", bomb.getArea().toString().toLowerCase(), message.getSender().getName());
                        System.out.println(" *** GAME OVER ***\nPlease press ENTER to continue.");

                        IPC.sendMessage(play, message.getSender(), MessageType.BOMB_AREA_MATCH, null, message.getBody(), true);
                    }
                }

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);

                if (play.getGameOver()) {
                    play.getRing().unlock();
                    play.getClient().exit();
                    play.getHandler().exit();
                    // play.closeIPC();
                }
                break;

            case BOMB_AREA_MATCH:
                if (!play.getGameOver()) {
                    play.getBombHits().add(message.getSender());
                }

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);
                break;

            case GAME_OVER:
                if (!play.getGameOver()) {
                    play.setGameOver(true);

                    play.getBombGenerator().exit();
                    play.getBombLauncher().exit();
                    play.exit(true);

                    System.out.println(message.getHead().get("output"));
                    System.out.println(" *** GAME OVER ***\nPlease press ENTER to continue.");
                }

                head.clear();
                head.put("ackIdx", message.getAckIdx());
                IPC.sendMessage(play, message.getSender(), MessageType.ACKNOWLEDGED, head, null, false);

                play.getRing().unlock();
                play.getClient().exit();
                play.getHandler().exit();
                // play.closeIPC();
                break;

            case SOCKET_CLOSURE:
                AddressPort key = new AddressPort(message.getSender().getAddress(), message.getSender().getPort());
                play.getClient().closeSocket(key);
                break;
        }
    }
}