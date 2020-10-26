package model;


public class Client {

    public int sessionID;
    public boolean loggedIn;
    private static int sessionCounter = 0;

    public final int hubID;

    public Client() {
        loggedIn = false;
        sessionID = ++sessionCounter;
        hubID = -1;
    }

    public Client(int hubID) {
        loggedIn = false;
        sessionID = ++sessionCounter;
        this.hubID = hubID;
    }

}
