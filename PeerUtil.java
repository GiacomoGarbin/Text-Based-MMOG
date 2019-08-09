import java.util.ArrayList;
import java.util.Random;

interface CheckFunction<T> {
    boolean check(T args);
}

class QuitApplicationException extends Exception {}
class QuitGameException extends Exception {}

class MessageQueue {
    private ArrayList<Message> queue;

    public MessageQueue() {
        queue = new ArrayList<Message>();
    }

    public synchronized Message pop() {
        while (queue.isEmpty()) {
            try {
                wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
        return queue.remove(0);
    }

    public synchronized void push(Message message) {
        queue.add(message);
        notify();
    }
}

enum GridArea {
    GREEN,      // north-west
    RED,        // north-east
    BLUE,       // south-west
    YELLOW,     // south-east
}

class Position {
    private int x;
    private int y;
    private int grid;

    private GridArea area;

    public Position() {}

    public Position(int grid) {
        this.grid = grid;
    }

    public int getX() { return x; }
    public void setX(int number) { x = number; }

    public int getY() { return y; }
    public void setY(int number) { y = number; }

    public int getGrid() { return grid; }
    public void setGrid(int number) { grid = number; }

    public void setRandomPosition() {
        Random random = new Random();
        x = random.nextInt(grid);
        y = random.nextInt(grid);
    }

    @Override
    public boolean equals(Object obj) {
        Position tmp = (Position) obj;
        return tmp.x == this.x && tmp.y == this.y;
    }

    public GridArea getArea() {
        int halfGrid = grid / 2;

        if (x < halfGrid) {
            if (y < halfGrid) {
                return GridArea.GREEN;
            } else {
                return GridArea.BLUE;
            }
        } else {
            if (y < halfGrid) {
                return GridArea.RED;
            } else {
                return GridArea.YELLOW;
            }
        }
    }

    public void setArea(GridArea area) { this.area = area; }

    @Override
    public String toString() {
        return String.format("%d, %d (%s area)", x, y, getArea().toString().toLowerCase());
    }

    public void decX() { x = (x > 0 ? x : grid) - 1; }
    public void incX() {
        x = (x + 1) % grid;
    }
    public void decY() {
        y = (y > 0 ? y : grid) - 1;
    }
    public void incY() {
        y = (y + 1) % grid;
    }
}

class PositionQueue {
    private ArrayList<Position> queue;
    private int capacity;

    public PositionQueue(int capacity ) {
        queue = new ArrayList<Position>();
        this.capacity = capacity;
    }

    public synchronized Position[] pop() {
        while (queue.size() < capacity) {
            try {
                wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
        return (Position[]) queue.toArray(new Position[queue.size()]);
    }

    public synchronized void push(Position position) {
        queue.add(position);
        if (queue.size() == capacity) {
            notify();
        }
    }
}

class Neighbors {
    private User prev;
    private User next;

    public Neighbors() {}

    public Neighbors(User prev, User next) {
        this.prev = prev;
        this.next = next;
    }

    public User getPrev() { return prev; }
    public void setPrev(User user) { prev = user; }

    public User getNext() { return next; }
    public void setNext(User user) { next = user; }
}