package model;

import java.util.concurrent.BlockingQueue;

public class Client_Hub extends Client {

    /**
     * Home-servers (hubs) connecting to the public server
     *
     * Needs to be separable from User clients,
     * e.g. to verify that a User (Android/Browser client) does not
     * attempt to make a request only permitted by
     */

    public String alias;

    public Client_Hub(int hubID, String ip, BlockingQueue<String> outputQueue, String alias) {
        super(hubID, ip, outputQueue);
        this.alias = alias;
    }
}
