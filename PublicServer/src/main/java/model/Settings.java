package model;

public class Settings {
    // Settings are loaded in from 'config.json' at system boot.
    private boolean debugMode; // Trigger additional logging
    private int serverPort;
    private int clientLimit; // Max umber of simultaneously connected clients
    // DB specs
    private String dbIP;
    private String dbPort; // Used as String when connecting to DB server
    private String dbDatabase;
    private String dbAccount;
    private String dbPassword;

    // ===================================== GETTERS & SETTERS =============================================

    public String[] getDbSpecs() {
        String[] dbSpecs = {dbIP, dbPort, dbDatabase, dbAccount, dbPassword};

        dbAccount = ""; // Clear sensitive data
        dbPassword =""; // Clear sensitive data

        return dbSpecs;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public int getServerPort() {
        return serverPort;
    }

    public int getClientLimit() {
        return clientLimit;
    }
}
