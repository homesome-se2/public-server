package model;


public class Client_User extends Client {
    /**
     * Client_User can be either Android client or browser client.
     *
     * If it turns out browser clients need different implementation
     * than Android; specialize 2 sub classes of Client_User:
     * Client_Android and Client_Browser (but should not be needed)
     */

    //TODO Use email address instead as unique identifier?
    private final String nameID;
    private final boolean admin;
    private final String sessionKey;


    public Client_User(int hubID, String nameID, boolean admin,String sessionKey) {
        super(hubID);
        this.nameID = nameID;
        this.admin = admin;
        this.sessionKey = sessionKey;
        loggedIn = true; // logged in for specialized client
    }

    public String getNameID() {
        return nameID;
    }

    public boolean isAdmin() {
        return admin;
    }

    public String getSessionKey() {
        return sessionKey;
    }
}
