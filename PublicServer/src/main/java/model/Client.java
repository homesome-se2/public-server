package model;

import java.util.concurrent.BlockingQueue;

public class Client {

    // Client is either a hub itself, or a user that should be mapped to a hub.
    public final int hubID;
    // Just for logging purposes.
    public final String ip;
    // Pass by reference. ClientThread listens for items in outputQueue.
    private BlockingQueue<String> outputQueue;

    public Client(int hubID, String ip, BlockingQueue<String> outputQueue) {
        this.hubID = hubID;
        this.ip = ip;
        this.outputQueue = outputQueue;
    }

    public void addToOutputQueue(String msg) {
        try {
            outputQueue.put(msg);
        } catch (InterruptedException e) {
            // Ignore. Small risk, little system impact.
        }
    }

}
