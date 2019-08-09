import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class AddressPort {
    private String address;
    private int port;

    public AddressPort(String address, int port) {
        this.address = address;
        this.port = port;
    }

    @Override
    public boolean equals(Object obj) {
        AddressPort tmp = (AddressPort) obj;
        return address.equals(tmp.address) && (port == tmp.port);
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return address + ":" + port;
    }
}

class User {
    private String name;
    private String address;
    private int port;

    public User() {}

    public User(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    public String getName() { return name; }
    public void setName(String string) { name = string; }

    public String getAddress() { return address; }
    public void setAddress(String string) { address = string; }

    public int getPort() { return port; }
    public void setPort(int number) { port = number; }

    @Override
    public boolean equals(Object obj) {
        User user = (User) obj;
        return this.address.equals(user.getAddress()) && this.port == user.getPort();
    }

    @Override
    public String toString() {
        return Json.toJson(this);
    }
}

class Game {
    private String name;            // game name
    private int grid;               // grid length
    private int score;              // target score
    private ArrayList<User> users;  // user list

    public Game() {}

    public Game(String name, int grid, int score) {
        this.name = name;
        this.grid = grid;
        this.score = score;
        users = new ArrayList<User>();
    }

    public String getName() { return name; }
    public void setName(String string) { name = string; }

    public int getGrid() { return grid; }
    public void setGrid(int number) { grid = number; }

    public int getScore() { return score; }
    public void setScore(int number) { score = number; }

    public ArrayList<User> getUsers() { return users; }
    public void setUsers(ArrayList<User> list) { users = list; }
}

enum MessageType {
    OK,

    TOKEN,

    ACKNOWLEDGED,

    USER_NOT_EXIST,
    USER_ADDRESSPORT_UNAVAILABLE,

    GAME_NAME_UNAVAILABLE,
    GAME_NOT_EXIST,
    GAME_USER_DUPLICATION,
    GAME_USER_WELCOME,
    GAME_USER_LEFT,
    GAME_OVER,

    RING_ENTRY_REQUEST,
    RING_ENTRY_FAILED,
    RING_ENTRY_SUCCEEDED,

    NEIGHBORS_UPDATE,
    USER_LIST_UPDATE,
    SOCKET_CLOSURE,

    POSITION_REQUEST,
    POSITION_VALUE,
    POSITION_UPDATE,
    POSITION_CHECK,
    POSITION_MATCH,

    BOMB_LAUNCH,
    BOMB_EXPLOSION,
    BOMB_AREA_MATCH,
}

class Message {
    private MessageType type;
    private User sender;
    private User recipient;
    private HashMap<String, String> head;
    private String body;
    private String ackIdx;

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public User getSender() { return sender; }
    public void setSender(User user) { sender = user; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User user) { recipient = user; }

    public HashMap<String, String> getHead() { return head; }
    public void setHead(HashMap<String, String> map) { head = map; }

    public String getBody() { return body; }
    public void setBody(String string) { body = string; }

    public String getAckIdx() { return ackIdx; }
    public void setAckIdx(String string) { ackIdx = string; }

    @Override
    public String toString() {
        return Json.toJson(this);
    }
}

class Json {
    private static ObjectMapper mapper = new ObjectMapper();

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException exc) {
            exc.printStackTrace();
        }
        return null;
    }

    public static Object fromJson(String json, Class cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (IOException exc) {
            exc.printStackTrace();
        }
        return null;
    }
}

class TimeoutException extends Exception {}

class Semaphore {
    private int permits;
    private int counter;

    private boolean timeout;

    public Semaphore(int permits) {
        this.permits = this.counter = permits;

        this.timeout = true;
    }

    public Semaphore(int permits, int counter) {
        this.permits = permits;
        this.counter = counter;

        this.timeout = true;
    }

    public synchronized void acquire() {
        while (counter <= 0) {
            try {
                wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
        --counter;
    }

    public synchronized void acquire(long timeout) throws TimeoutException {
        while (counter <= 0) {
            try {
                wait(timeout);
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }

            if (this.timeout) {
                throw new TimeoutException();
            }
        }
        --counter;
    }

    public synchronized void release() {
        counter = Math.min(counter + 1, permits);

        this.timeout = false;

        notify();   // notifyAll();
    }
}