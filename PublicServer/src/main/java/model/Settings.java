package main.java.model;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    //Note: 'config.json' should be located "next to" the project folder: [config.json][PublicServer]

    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
     private static final String configFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/public-server/config.json"); // When run from IDE
    public Settings(){}

    public void readInSettings() throws Exception {
        FileReader reader = null;

        try {
            Gson gson = new Gson();
            reader = new FileReader(configFileJSON);

            Settings s = gson.fromJson(reader, Settings.class);
            debugMode = s.debugMode;
            serverPort = s.serverPort;
            clientLimit = s.clientLimit;
            dbIP = s.dbIP;
            dbPort = s.dbPort;
            dbDatabase = s.dbDatabase;
            dbAccount = s.dbAccount;
            dbPassword = s.dbPassword;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            throw new Exception("Unable to read settings from config.json");
        } finally {
            if(reader != null) {
                reader.close();
            }
        }
    }

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
