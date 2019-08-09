import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Peer {
    // methods

    private static User login(String[] args) {
        String name;
        String address = null;
        int port;

        try {
            address = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException exc) {
            exc.printStackTrace();
        }

        if (args.length > 0) {

            name = args[0];
            port = Integer.parseInt(args[1]);

        } else {

            CheckFunction<String> checkName = (input) -> {
                Pattern pattern = Pattern.compile("[a-zA-Z0-9_]+");
                Matcher matcher = pattern.matcher(input);

                if (!matcher.matches()) {
                    System.out.println("Sorry, the name can only contain alphanumeric characters and the underscore character.");
                    return false;
                }

                return true;
            };

            final String addressFinal = address;

            CheckFunction<String> checkPort = (input) -> {
                Pattern pattern = Pattern.compile("[0-9]+");
                Matcher matcher = pattern.matcher(input);

                if (!matcher.matches()) {
                    System.out.println("Incorrect port number, please use only numeric characters.");
                    return false;
                }

                int tmp = Integer.parseInt(input);

                if (!(1023 < tmp && tmp <= 65535)) {
                    System.out.println("Incorrect port number, must be between 1024 and 65535.");
                    return false;
                }

                return true;
            };

            String input;

            do {
                System.out.println("Which name do you want to use?");
                input = getInput();
            } while (input == null || !checkName.check(input));

            name = input;

            do {
                System.out.println("Which port do you want to use?");
                input = getInput();
            } while (input == null || !checkPort.check(input));

            port = Integer.parseInt(input);

        }

        User user = new User(name, address, port);

        MessageType result = SRC.addUser(user);

        if (result != MessageType.OK) {
            System.out.println("Sorry, this port number is already used by another player with your same IP address.");
            return null;
        }

        return user;
    }

    private static boolean logout(User user) {
        String key = user.getAddress() + "-" + user.getPort();

        MessageType result = SRC.removeUser(key);
        if (result != MessageType.OK) {
            // ...
            return false;
        }

        return true;
    }

    private static Game chooseGame() throws QuitApplicationException {
        Game[] games = SRC.gameList();

        int j = 0;
        int n = 1 + (int) Math.log10(games.length);

        System.out.println("The games in progress are:");
        if(games.length == 0) {
            System.out.println(" *** there are no games in progress ***");
        } else {
            for (Game game : games) {
                System.out.format(" %" + n + "d. %s%n", ++j, game.getName());
            }
        }
        System.out.println(
            "To choose a game type the corresponding number,\n" +
            "to create one type 0, to quit type Q."
        );

        CheckFunction<String> checkInput = (input) -> {
            Pattern pattern = Pattern.compile("[0-9]+|q|Q");
            Matcher matcher = pattern.matcher(input);

            if (!matcher.matches()) {
                System.out.println("Sorry, you typed an invalid character.");
                return false;
            }

            pattern = Pattern.compile("[0-9]+");
            matcher = pattern.matcher(input);

            if (matcher.matches() && Integer.parseInt(input) > games.length) {
                System.out.println("Sorry, you have selected a non-existent or finished game.");
                return false;
            }

            return true;
        };

        String input;

        do {
            input = getInput();
        } while (!checkInput.check(input));

        switch (input) {
            case "0":
                return newGame();
            case "q":
            case "Q":
                throw new QuitApplicationException();
            default:
                int idx = Integer.parseInt(input) - 1;
                return viewGame(games[idx].getName());
        }
    }

    private static Game newGame() {
        String name;
        int grid;
        int score;

        CheckFunction<String> checkName = (input) -> {
            Pattern pattern = Pattern.compile("[a-zA-Z0-9_]+");
            Matcher matcher = pattern.matcher(input);

            if (!matcher.matches()) {
                System.out.println("Sorry, the name can only contain alphanumeric characters and the underscore character.");
                return false;
            }

            return true;
        };

        CheckFunction<String> checkGrid = (input) -> {
            Pattern pattern = Pattern.compile("[0-9]+");
            Matcher matcher = pattern.matcher(input);

            if (!matcher.matches()) {
                System.out.println("Sorry, you typed an invalid character.");
                return false;
            }

            int tmp = Integer.parseInt(input);

            if (!((tmp > 0) && (tmp % 2 == 0))) {
                System.out.println("Sorry, the grid size must be an even positive number.");
                return false;
            }

            return true;
        };

        CheckFunction<String> checkScore = (input) -> {
            Pattern pattern = Pattern.compile("[0-9]+");
            Matcher matcher = pattern.matcher(input);

            if (!matcher.matches()) {
                System.out.println("Sorry, you typed an invalid character.");
                return false;
            }

            if (Integer.parseInt(input) == 0) {
                System.out.println("Sorry, the target score must be a positive number.");
                return false;
            }

            return true;
        };

        System.out.println(" *** CREATING A NEW GAME ***");

        String input;

        System.out.println("Please, type the name of the game.");
        do {
            input = getInput();
        } while (!checkName.check(input));

        name = input;

        System.out.println("Please, type the size of the grid.");
        do {
            input = getInput();
        } while (!checkGrid.check(input));

        grid = Integer.parseInt(input);

        System.out.println("Please, type the target score.");
        do {
            input = getInput();
        } while (!checkScore.check(input));

        score = Integer.parseInt(input);

        Game game = new Game(name, grid, score);

        if (SRC.addGame(game) == MessageType.GAME_NAME_UNAVAILABLE) {
            System.out.println("Sorry, this name is already used by a game in progress.");
            return null;
        } else {
            return game;
        }
    }

    private static Game viewGame(String name) {
        Message response = SRC.viewGame(name);

        if (response.getType() == MessageType.GAME_NOT_EXIST) {
            System.out.println("Sorry, you have selected a non-existent or finished game.");
            return null;
        }

        Game game = (Game) Json.fromJson(response.getBody(), Game.class);

        System.out.format("Game \"%s\".%n", game.getName());
        System.out.format("Grid of size: %d%n", game.getGrid());
        System.out.format("Target score: %d%n", game.getScore());
        System.out.println("The players are:");

        if(game.getUsers().isEmpty()) {
            System.out.println(" *** there are no players in the game ***");
        } else {
            for (User user : game.getUsers()) {
                System.out.format(" * %s%n", user.getName());
            }
        }

        System.out.println("Confirm your entry to the game? (Y/N)");

        CheckFunction<String> checkAnswer = (input) -> {
            Pattern pattern = Pattern.compile("y|Y|n|N");
            Matcher matcher = pattern.matcher(input);

            if (!matcher.matches()) {
                System.out.println("Sorry, you typed an invalid character.");
                return false;
            }

            return true;
        };

        String input;

        do {
            input = getInput();
        } while (!checkAnswer.check(input));

        return (input.equals("n") || input.equals("N")) ? null : game;
    }

    // keyboard input

    private static BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
    private static String getInput() {
        String input = null;
        try {
            input = bufferedReader.readLine();
        } catch (IOException exc) {
            exc.printStackTrace();
        }
        return input;
    }

    // main

    public static void main(String[] args) {
        User user;
        Game game;

        System.out.println(" *** Welcome to MMOG! ***");

        do {
            user = login(args);
        } while (user == null);

        try {

            while (true) {
                do {
                    game = chooseGame();
                } while (game == null);

                try {
                    (new PlayGame(user, game)).join();
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }

                System.out.println("Do you want to play another game? (Y/N)");

                CheckFunction<String> checkAnswer = (input) -> {
                    Pattern pattern = Pattern.compile("y|Y|n|N");
                    Matcher matcher = pattern.matcher(input);

                    if (!matcher.matches()) {
                        System.out.println("Sorry, you typed an invalid character.");
                        return false;
                    }

                    return true;
                };

                String input;

                do {
                    input = getInput();
                } while (!checkAnswer.check(input));

                if (input.equals("n") || input.equals("N")) {
                    throw new QuitApplicationException();
                }
            }

        } catch (QuitApplicationException exc) {
            System.out.println("Thanks for playing, see you! ;)");
            logout(user);
        }
    }
}