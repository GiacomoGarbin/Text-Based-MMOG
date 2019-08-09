import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

class Bomb {
    private GridArea area;

    public Bomb() {}

    public Bomb(GridArea area) {
        this.area = area;
    }

    public GridArea getArea() { return area; }
    public void setArea(GridArea area) { this.area = area; }
}

class BombBag {
    private ArrayList<Bomb> bombs;

    public BombBag() {
        bombs = new ArrayList<Bomb>();
    }

    public synchronized Bomb pop() {
        return bombs.isEmpty() ? null : bombs.remove(0);
    }

    public synchronized void push(Bomb bomb) {
        bombs.add(bomb);
    }
}

class MeasurementBuffer implements Buffer<Measurement> {
    private ArrayList<Measurement> buffer;

    public MeasurementBuffer() {
        buffer = new ArrayList<Measurement>();
    }

    public synchronized void addNewMeasurement(Measurement measure) {
        buffer.add(measure);
    }

    public synchronized List<Measurement> readAllAndClean() {
        List<Measurement> measures = Arrays.asList(buffer.toArray(new Measurement[buffer.size()]));
        buffer.clear();
        return measures;
    }
}

class BombGenerator extends Thread {
    private MeasurementBuffer buffer;
    private AccelerometerSimulator simulator;
    private Thread thread;

    private BombBag bombs;
    private double alpha;
    private double threshold;

    private boolean running;

    public BombGenerator(BombBag bombs, double alpha, double threshold) {
        buffer = new MeasurementBuffer();
        simulator = new AccelerometerSimulator(buffer);
        thread = new Thread(simulator);

        this.bombs = bombs;
        this.alpha = alpha;
        this.threshold = threshold;

        running = true;

        // start();
    }

    public void run() {
        ArrayList<Measurement> measures = new ArrayList<Measurement>();
        double avg, emaOld, emaNew;

        thread.start();

        // warm up

        try {
            Thread.sleep(1000);
        } catch (InterruptedException exc) {
            exc.printStackTrace();
        }

        measures.addAll(buffer.readAllAndClean());

        avg = AVG(measures);
        emaOld = avg;

        measures.clear();

        while (running) {
            synchronized (this) {
                try {
                    wait(1000);
                } catch (InterruptedException exc) {
                    exc.printStackTrace();
                }
            }

            if (!running) {
                break;
            }

            measures.addAll(buffer.readAllAndClean());

            avg = AVG(measures);
            emaNew = EMA(emaOld, avg);

            if (Math.abs(emaNew - emaOld) > threshold) {
                // outlier
                Bomb bomb = new Bomb(GridArea.values()[(int) Math.ceil(emaNew) % 4]);
                System.out.format("A new %s bomb has been added to your bomb bag! >:)\n", bomb.getArea().toString().toLowerCase());

                bombs.push(bomb);
            }

            measures.clear();
        }

        simulator.stopMeGently();
    }

    public synchronized void exit() {
        running = false;
        notify();
    }

    // Average
    private double AVG(List<Measurement> measures) {
        double sum = 0.0;

        for (Measurement measure : measures) {
            sum += measure.getValue();
        }

        return sum / measures.size();
    }

    // Exponential Moving Average
    private double EMA(double ema, double avg) {
        return ema + alpha * (avg - ema);
    }
}

class BombQueue {
    private ArrayList<Bomb> queue;

    public BombQueue() {
        queue = new ArrayList<Bomb>();
    }

    public synchronized Bomb pop() {
        while (queue.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
        return queue.remove(0);
    }

    public synchronized void push(Bomb bomb) {
        queue.add(bomb);
        notify();
    }
}

class BombLauncher extends Thread {
    private PlayGame play;
    ArrayList<BombLauncherChild> children;

    public BombLauncher(PlayGame play) {
        this.play = play;
        children = new ArrayList<BombLauncherChild>();

        // start();
    }

    public void run() {
        while (true) {
            // clean up
            synchronized (this) {
                Iterator<BombLauncherChild> iter = children.iterator();

                while (iter.hasNext()) {
                    BombLauncherChild child = iter.next();

                    if (child.getState() == Thread.State.TERMINATED) {
                        iter.remove();
                    }
                }
            }

            Bomb bomb = play.getBombQueue().pop();

            if (bomb == null) {
                break;
            }

            synchronized (this) {
                children.add(new BombLauncherChild(play, bomb));
            }
        }

        for (BombLauncherChild child : children) {
            child.exit();

            try {
                child.join();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }

        // all the bombs have been disarmed
    }

    public void exit() {
        play.getBombQueue().push(null);
    }

    public synchronized boolean stillBomb() {
        for (BombLauncherChild child : children) {
            if (child.getState() != Thread.State.TERMINATED) {
                return true;
            }
        }
        return false;
    }
}

class BombLauncherChild extends Thread {
    private PlayGame play;
    private Bomb bomb;
    private boolean defused;

    public BombLauncherChild(PlayGame play, Bomb bomb) {
        this.play = play;
        this.bomb = bomb;
        defused = false;

        start();
    }

    public void run() {
        synchronized (this) {
            try {
                wait(5000);
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }

        if (!defused) {
            play.getMutex().acquire();
            play.getRing().requestCS();

            if (play.getGameOver()) {
                // ring.releaseCS();
                play.getMutex().release();
                return;
            }

            System.out.format("KABOOM! Your %s bomb exploded...\n", bomb.getArea().toString().toLowerCase());

            // sending BOMB_EXPLOSION messages must be serialized

            User[] users;

            users = play.getGame().getUsers().toArray(new User[play.getGame().getUsers().size()]);
            for (User user : users) {
                IPC.sendMessage(play, user, MessageType.BOMB_EXPLOSION, null, Json.toJson(bomb), true);
            }

            for (User user : play.getBombHits()) {
                AddressPort key = new AddressPort(user.getAddress(), user.getPort());
                play.getClient().closeSocket(key);
            }

            if (play.getPosition().getArea() == bomb.getArea()) {
                play.setGameOver(true);

                play.getBombGenerator().exit();
                play.getBombLauncher().exit();
                play.exit(true);

                System.out.println("Oh no, you've been hit by your own bomb! :(");
                System.out.println(" *** GAME OVER ***\nPlease press ENTER to continue.");
            } else {
                int hits = play.getBombHits().size();
                int points = Math.min(hits, 3);
                play.setScore(play.getScore() + points);

                System.out.format("The bomb hit %d players and your score is increased by %d points! :)\n", hits, points);

                if (play.getScore() >= play.getGame().getScore()) {
                    play.setGameOver(true);

                    play.getBombGenerator().exit();
                    play.getBombLauncher().exit();

                    // sending GAME_OVER messages must be serialized

                    HashMap<String, String> head = new HashMap<String, String>();
                    head.put("output", String.format("Oh no, %s reached the target score and won the game! :(", play.getUser().getName()));

                    users = play.getGame().getUsers().toArray(new User[play.getGame().getUsers().size()]);
                    for (User user : users) {
                        IPC.sendMessage(play, user, MessageType.GAME_OVER, head, null, true);
                    }

                    System.out.format("Congratulations you reached the %d points target score and won the game! :D\n", play.getGame().getScore());
                    System.out.println(" *** GAME OVER ***\nPlease press ENTER to continue.");

                    play.exit(true);
                }
            }

            play.getBombHits().clear();

            play.getRing().releaseCS();
            play.getMutex().release();

            if (play.getGameOver()) {
                play.getRing().unlock();
                play.getClient().exit();
                play.getHandler().exit();
                // play.closeIPC();
            }
        } else {
            // pfff...
        }
    }

    public synchronized void exit() {
        defused = true;
        notify();
    }
}