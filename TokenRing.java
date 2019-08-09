class TokenRing {
    private boolean haveToken;
    private boolean wantCS;
    private PlayGame play;

    public TokenRing(boolean flag, PlayGame play) {
        haveToken = flag;
        wantCS = false;
        this.play = play;

        sendToken();
    }

    public synchronized void requestCS() {
        wantCS = true;
        while (!haveToken) {
            try {
                wait();
            } catch (InterruptedException exc) {
                exc.printStackTrace();
            }
        }
    }

    public synchronized void releaseCS() {
        wantCS = false;
        sendToken();
    }

    public void sendToken() {
        if (haveToken && !wantCS) {
            haveToken = false;
            IPC.sendMessage(play, play.getNext(), MessageType.TOKEN, null, null, false);
        }
    }

    public synchronized void handleMessage(Message message) {
        if (message.getType() == MessageType.TOKEN) {
            haveToken = true;
            if (wantCS) {
                notify();
            } else {
                sendToken();
            }
        }
    }

    public synchronized void unlock() {
        wantCS = false;
        haveToken = true;
        notify();
    }
}