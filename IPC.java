// Inter-Process Communication

import java.io.*;
import java.net.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

class Server extends Thread {
    ServerSocket serverSocket;
    MessageQueue queue;

    Server(int port, MessageQueue queue) {
        this.queue = queue;

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException exc) {
            exc.printStackTrace();
        }

        start();
    }

    public void run() {
        ArrayList<ServerChild> children = new ArrayList<ServerChild>();

        while (true) {
            // clean up
            Iterator<ServerChild> iter = children.iterator();

            while (iter.hasNext()) {
                ServerChild child = iter.next();

                if (child.getState() == Thread.State.TERMINATED) {
                    iter.remove();
                }
            }

            try {
                Socket socket = serverSocket.accept();
                children.add(new ServerChild(socket, queue));
            } catch (IOException exc) {
                // exc.printStackTrace();
                break;
            }
        }

        for (ServerChild child : children) {
            try {
                child.join();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
    }

    public void exit() {
        try {
            serverSocket.close();
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }
}

class ServerChild extends Thread {
    Socket socket;
    DataInputStream dataInputStream;
    MessageQueue queue;

    ServerChild(Socket socket, MessageQueue queue) {
        this.socket = socket;
        this.queue = queue;

        try {
            dataInputStream = new DataInputStream(socket.getInputStream());
        } catch (IOException exc) {
            exc.printStackTrace();
        }

        start();
    }

    public void run() {
    	while (true) {
            try {
                String message = dataInputStream.readUTF();
                queue.push((Message) Json.fromJson(message, Message.class));
            } catch (EOFException exc) {
                // exc.printStackTrace();
                break;
            } catch (IOException exc) {
                exc.printStackTrace();
                break;
            }
        }

        try {
            socket.close();
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }
}

class SocketList {
    private ArrayList<Socket> sockets;

    public SocketList() {
        sockets = new ArrayList<Socket>();
    }

    public synchronized void add(Socket socket) {
        sockets.add(socket);
    }

    public synchronized void close(AddressPort key) {
        Iterator<Socket> iter = sockets.iterator();

        while (iter.hasNext()) {
            Socket socket = iter.next();

            if (key.equals(new AddressPort(socket.getInetAddress().getHostAddress(), socket.getPort()))) {
                try {
                    socket.close();
                } catch (IOException exc) {
                    exc.printStackTrace();
                }

                iter.remove();
                break;
            }
        }
    }

    public synchronized void closeAll() {
        Iterator<Socket> iter = sockets.iterator();

        while (iter.hasNext()) {
            Socket socket = iter.next();

            try {
                socket.close();
            } catch (IOException exc) {
                exc.printStackTrace();
            }

            iter.remove();
        }
    }

    public synchronized boolean contains(AddressPort key) {
        Iterator<Socket> iter = sockets.iterator();

        while (iter.hasNext()) {
            Socket socket = iter.next();

            if (key.equals(new AddressPort(socket.getInetAddress().getHostAddress(), socket.getPort()))) {
                return true;
            }
        }

        return false;
    }

    public synchronized Socket get(AddressPort key) {
        Iterator<Socket> iter = sockets.iterator();

        while (iter.hasNext()) {
            Socket socket = iter.next();

            if (key.equals(new AddressPort(socket.getInetAddress().getHostAddress(), socket.getPort()))) {
                return socket;
            }
        }

        return null;
    }

    public synchronized Socket remove(AddressPort key) {
        Iterator<Socket> iter = sockets.iterator();

        while (iter.hasNext()) {
            Socket socket = iter.next();

            if (key.equals(new AddressPort(socket.getInetAddress().getHostAddress(), socket.getPort()))) {
                iter.remove();
                return socket;
            }
        }

        return null;
    }

    @Override
    public synchronized String toString() {
        return sockets.toString();
    }
}

class Client extends Thread {
    SocketList sockets;
    MessageQueue queue;

    public Client(MessageQueue queue) {
        sockets = new SocketList();
        this.queue = queue;

        start();
    }

    public void run() {
        ArrayList<ClientChild> children = new ArrayList<ClientChild>();

        while (true) {
            // clean up
            Iterator<ClientChild> iter = children.iterator();

            while (iter.hasNext()) {
                ClientChild child = iter.next();

                if (child.getState() == Thread.State.TERMINATED) {
                    iter.remove();
                }
            }

            Message message = queue.pop();

            if (message == null) {
                break;
            }

            AddressPort key = new AddressPort(message.getRecipient().getAddress(), message.getRecipient().getPort());

            Socket socket = null;

            if (sockets.contains(key)) {
                socket = sockets.get(key);
            } else {
                try {
                    socket = new Socket(key.getAddress(), key.getPort());
                } catch (ConnectException exc) {
                    // exc.printStackTrace();
                    continue;
                } catch (IOException exc) {
                    exc.printStackTrace();
                    continue;
                }
                sockets.add(socket);
            }

            children.add(new ClientChild(socket, message));
        }

        for (ClientChild child : children) {
            try {
                child.join();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }

        sockets.closeAll();
    }

    public void exit() {
        queue.push(null);
    }

    public void closeSocket(AddressPort key) {
        sockets.close(key);
        // sockets.remove(key);
    }
}

class ClientChild extends Thread {
    Socket socket;
    DataOutputStream dataOutputStream;
    Message message;

    ClientChild(Socket socket, Message message) {
        this.socket = socket;
        this.message = message;

        try {
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
        } catch (IOException exc) {
            exc.printStackTrace();
        }

        start();
    }

    public void run() {
        try {
            dataOutputStream.writeUTF(Json.toJson(message));
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }
}

class IPC {
    private static final int padding = 1 + (int) Math.log10(Long.MAX_VALUE);
    private static long ackCounter = 0;

    // single recipient
    public static void sendMessage(PlayGame play, User recipient, MessageType type, HashMap<String, String> head, String body, boolean ackFlag) {
        Message message = new Message();
        message.setType(type);
        message.setSender(play.getUser());
        message.setRecipient(recipient);
        message.setHead(head);
        message.setBody(body);

        String ackIdx;

        synchronized (IPC.class) {
            ackCounter = 1 + ackCounter % Long.MAX_VALUE;
            ackIdx = String.format("%0" + padding + "d.%0" + padding + "d", Instant.now().toEpochMilli(), ackCounter);
        }

        message.setAckIdx(ackIdx);

        if (ackFlag) {
            play.getAckMap().put(ackIdx, new SingleAck());
        }

        play.getOQueue().push(message);

        if (ackFlag) {
            play.getAckMap().get(ackIdx).requestAck();
        }
    }

    // multiple recipient
    public static void sendMessage(PlayGame play, ArrayList<User> recipient, MessageType type, HashMap<String, String> head, String body, boolean ackFlag) {
        String ackIdx;

        synchronized (IPC.class) {
            ackCounter = 1 + ackCounter % Long.MAX_VALUE;
            ackIdx = String.format("%0" + padding + "d.%0" + padding + "d", Instant.now().toEpochMilli(), ackCounter);
        }

        if (ackFlag) {
            play.getAckMap().put(ackIdx, new MultipleAck(recipient.size()));
        }

        for (User user : recipient) {
            Message message = new Message();
            message.setType(type);
            message.setSender(play.getUser());
            message.setRecipient(user);
            message.setHead(head);
            message.setBody(body);
            message.setAckIdx(ackIdx);

            play.getOQueue().push(message);
        }

        if (ackFlag) {
            play.getAckMap().get(ackIdx).requestAck();
        }
    }
}