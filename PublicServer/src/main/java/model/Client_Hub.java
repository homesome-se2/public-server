package main.java.model;


public class Client_Hub extends Client {

    /**
     * Home-servers (hubs) connecting to the public server
     *
     * Needs to be separable from User clients,
     * e.g. to verify that a User (Android/Browser client) does not
     * attempt to make a request only permitted by
     */

    public String alias;

    public Client_Hub(int hubID, String alias) {
        super(hubID);
        this.alias = alias;
        loggedIn = true; // logged in for specialized client
    }
}
