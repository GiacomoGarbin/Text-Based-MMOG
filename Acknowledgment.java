interface Acknowledgment {
    void requestAck();
    void releaseAck();
    boolean isReleased();
}

class SingleAck implements Acknowledgment {
    private boolean flag;

    public SingleAck() {
        flag = false;
    }

    public synchronized void requestAck() {
        while (!flag) {
            try {
                wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
    }

    public synchronized void releaseAck() {
        flag = true;
        notify();
    }

    public synchronized boolean isReleased() {
        return flag;
    }
}

class MultipleAck implements Acknowledgment {
    private int acks;

    public MultipleAck(int acks) {
        this.acks = acks * (-1);
    }

    public synchronized void requestAck() {
        while (acks < 0) {
            try {
                wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
    }

    public synchronized void releaseAck() {
        if (++acks >= 0) {
            notify();
        }
    }

    public synchronized boolean isReleased() {
        return acks >= 0;
    }
}