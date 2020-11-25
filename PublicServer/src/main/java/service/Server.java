package service;

import DAO.DB_Clients;
import com.google.gson.Gson;
import model.ClientRequest;
import model.Settings;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Server {

    public BlockingQueue<ClientRequest> clientRequests;
    public volatile Settings settings;
    public volatile boolean terminateServer;
    public  DB_Clients clientDB;
    // config.json
    //Note: 'config.json' should be located "next to" the project folder: [config.json][PublicServer]
    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
   // private static final String configFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/config.json"); // When run from IDE
    private static final String configFileJSON = "config.json"; // When run from IDE

    // Lock objects
    private final Object lock_closeServer;
    private final Object lock_debugLogs;

    // Make Singleton
    private static Server instance = null;

    public static Server getInstance() {
        if (instance == null) {
            instance = new Server();
        }
        return instance;
    }

    private Server() {
        clientRequests = new ArrayBlockingQueue<>(10);
        terminateServer = false;
        lock_closeServer = new Object();
        lock_debugLogs = new Object();

    }

    public void launch() {
        System.out.println("HomeSome server running...");
        try {
            // Read in settings from JSON
            readInSettings();

            // Launch ClientHandler
            ClientHandler.getInstance().launchWebSocketServer(settings.getServerPort(), settings.getClientLimit());
            processRequests();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            close();
        }
    }

    public void close() {
        synchronized (lock_closeServer) {
            if (!terminateServer) {
                terminateServer = true;
                ClientHandler.getInstance().stopWebSocketServer();
                System.out.println("HomeSome server shutting down");
            }
        }
    }

    private void readInSettings() throws Exception {
        try (FileReader reader = new FileReader(configFileJSON)) {
            settings = new Gson().fromJson(reader, Settings.class);
        } catch (FileNotFoundException e) {
            throw new Exception("Unable to read settings from config.json");
        }
    }

    //================================ PROCESS CLIENT REQUESTS ==============================================

    // Executed by worker thread: processRequestsThread
    private void processRequests() throws Exception {
        while (!terminateServer) {
            try {
                ClientRequest clientRequest = clientRequests.take();
                String commands[] = clientRequest.request.split("::");
                int sessionID = clientRequest.sessionID;

                try {
                    switch (commands[0]) {
                        case "105":
                            clientLogout(commands, sessionID);
                            break;
                        case "106":
                            clientLogoutAllDevices(commands,sessionID);
                            break;
                        case "301":
                            userRequestAllHubGadgets(sessionID);
                            break;
                        case "302":
                            serverRequestAllHubGadgets(commands, sessionID); //302::hub number, valid client session ID
                            break;
                        case "303":
                            receiveAllHubGadgets(commands, sessionID);
                            break;
                        case "311":
                            requestGadgetStateChange(commands, sessionID);
                            break;
                        case "315":
                            receiveGadgetStateChange(commands, sessionID);
                            break;
                        case "370":
                            requestGadgetGroups(sessionID);
                            break;
                        case "372":
                            receiveGadgetGroups(commands);
                            break;
                        default:
                            ClientHandler.getInstance().outputToClients(sessionID, false, true, false, "901::Invalid format");
                            break;
                    }
                } catch (Exception e) {
                    ClientHandler.getInstance().outputToClients(sessionID, false, true, false, "901::".concat(e.getMessage()));
                }
            } catch (InterruptedException e) {
                throw new Exception("Terminating processRequests()");
            } catch (Exception e) {
                // Ignore & carry on.
            }
        }
    }


    //TODO: Implement methods for all supported requests, according to HoSo protocol.

    /**
     * Notation above methods for forwarding scheme:
     * <p>
     * #303 -> #304  : The incoming request is a #303 request, and the forwarding request should be a #304 command.
     * #302 -> #302  : The forwarding is the same command (#302 -> #302)
     * #105 -> X     : No forwarding to be done.
     */

    // #105 -> 107
    private void clientLogout(String[] commands, int issuinSessionID) throws Exception {
        //TODO: Implement
        // User client (Android/browser) has manually pressed the logout button.
        // Remove/overwrite the client's sessionKey in DB. This would force a manual login next time client wants to connect.
        // This method returns nothing (possibly just an exception msg '901::xxxx' if something goes wrong).

        // Here it should bring the specific sessionKey for nameID and send it to the DB to delete it
        String currentUserSessionKey = ClientHandler.getInstance().getSessionKeyByUserSessionId(issuinSessionID);
        clientDB.logoutThisDevice(currentUserSessionKey);
        // 107
        String confirmLogout = String.format("107::%s", "This device is successfully logged out");
        ClientHandler.getInstance().outputToClients(issuinSessionID,false,true,false,confirmLogout);

    }

    // #106 -> 107
    private void clientLogoutAllDevices(String[] commands,int issuingSessionID) throws Exception {

        // Here it should bring the name ID for that user and send it to the DB to remove all sessionKey assigned to that user
        String nameId = ClientHandler.getInstance().getUserNameIdByUserSessionId(issuingSessionID);
        clientDB.logoutAllDevices(nameId);
        // 107
        String msg = String.format("107::%s", "successfully logout all devices assigned to your nameID");
        ClientHandler.getInstance().outputToClients(issuingSessionID,false,false,false,msg);
    }

    // #301 -> #302
    private void userRequestAllHubGadgets(int issuingSessionID) throws Exception {
        String forwardRequest = String.format("302::%s", issuingSessionID);
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(issuingSessionID);
        ClientHandler.getInstance().outputToClients(hubSessionID, true, true, false, forwardRequest);
    }

    // #302 -> #302
    // ps sends 302 to the hub to get all gadgets
    private void serverRequestAllHubGadgets(String[] commands, int issuinSessionID) throws Exception {
/*
        //302::clientSessionID
        // getting the session Id for the client requesting the gadgets and then forward it to the associated hub
        int clientSessionID = Integer.parseInt(commands[1]); // the hub number
        String forwardSessionId = String.format("%s::%s", "302", clientSessionID);
        //*** find the hub connected to him, then (get the hub session from the connected client list)<- is done by outPutToClients()
        // then pass the argument

        Session session = ClientHandler.getInstance().getSession(issuinSessionID);
        Client theClient = ClientHandler.getInstance().getConnectedClients().get(session);
        int hubID = theClient.hubID;
        ClientHandler.getInstance().outputToClients(hubID, true, true, false, forwardSessionId);
        //TODO: HubID is not the same as the hub's sessionID (which is the target for output)
        // mock hub answers
        // mock.hubReportsAllGadgets(issuinSessionID);
        // 302 from the client to the server is submitted as client request
        // 302 from the server to the hub, is sent as outputToClients(TO THE HUB)
        // 303 from hub to server, hubReportsAllGadgets();
        // 304 from server to client outputToClients(TO THE CLIENT WHO ISSUED THE REQUEST)
        */

        String forwardRequest = String.format("302::%s", commands[1]);
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(issuinSessionID);
        ClientHandler.getInstance().outputToClients(hubSessionID, true, true, false, forwardRequest);
    }

    // #303 -> #304
    private void receiveAllHubGadgets(String[] commands, int issuinSessionID) throws Exception {
        //303
        int targetSessionID = Integer.parseInt(commands[1]);// the client who issued the request
        int numberOfGadgets = Integer.parseInt(commands[2]);// the gadget information


        //304
        // Encapsulate (build) new command from the de-encapsulate incoming command (according to protocol)
        String forwardGadgetsMsg = String.format("%s::%s", "304", numberOfGadgets);
        for (int command = 3; command < commands.length; command++) {
            forwardGadgetsMsg = String.format("%s::%s", forwardGadgetsMsg, commands[command]);
        }
        // Send to individual client who issued the request using his sessionID
        ClientHandler.getInstance().outputToClients(targetSessionID, false, true, false, forwardGadgetsMsg);
    }

    // #311 -> #312
    private void requestGadgetStateChange(String[] commands, int cSessionID) throws Exception {
       /*
        int gadgetID;
        String newGadgetState;
        //#311 CLIENT -> PS

        gadgetID = Integer.parseInt(commands[1]);// the client who issued the request
        newGadgetState = commands[2];// the gadget information

        // #312 PS -> HUB
        Session session = ClientHandler.getInstance().getSession(cSessionID);
        Client theClient = ClientHandler.getInstance().getConnectedClients().get(session);
        int hubID = theClient.hubID;
        String forwardGadgetsMsg = String.format("%s::%s::%s", "312", gadgetID, newGadgetState);
        ClientHandler.getInstance().outputToClients(hubID, true, true, false, forwardGadgetsMsg);
        */

        String forwardRequest = String.format("312::%s::%s", commands[1], commands[2]);
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(cSessionID);
        ClientHandler.getInstance().outputToClients(hubSessionID, true, true, false, forwardRequest);
    }

    // #315 -> #316
    private void receiveGadgetStateChange(String[] commands, int issuingSessionID) throws Exception {
        // #315 HUB -> PS
        // 315::gadgetID::GadgetState

        String gadgetID = commands[1];
        String newState = commands[2];

        //#316 PS -> CLIENT
        // 316::gadgetID::GadgetState

        String forwardMsg = String.format("%s::%s::%s", "316", gadgetID, newState);
        // Send to all users associated with that hub -> th connection between the hub and the clients are figured by outputToAllClients()
        ClientHandler.getInstance().outputToClients(issuingSessionID, false, false, false, forwardMsg);
        //mock.hubReportsGadgetState();// answers with -> 316::gadgetID::GadgetState

    }

    // #370 -> #371
    private void requestGadgetGroups(int cSessionID) throws Exception{
       /*
        // #370 CLIENT -> PS---- NO ARGUMENTS      ---- DONE
        // #371 PS -> HUB ------ CLIENT SESSION ID ---- DONE
        // #372 HUB ->PS ------- CLIENT SESSION ID && [groupName]:[G_id]:[G_id]:[G_id]::[groupName]:[G_id]:[G_id] --- DONE
        // #373 PS -> CLIENT --- [groupName]:[G_id]:[G_id]:[G_id]::[groupName]:[G_id]:[G_id]

        // #371 PS -> HUB ---- DONE
        Session session = null;
        try {
            String forwardRequest = String.format("%s::%s", "371", cSessionID);
            session = ClientHandler.getInstance().getSession(cSessionID);
            Client theClient = ClientHandler.getInstance().getConnectedClients().get(session);
            int hubID = theClient.hubID;
            ClientHandler.getInstance().outputToClients(hubID, true, true, false, forwardRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //mock.requestGadgetGroups(issuingSessionID); //TODO: REMOVE LATER
        */

        String forwardRequest = String.format("371::%s", cSessionID);
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(cSessionID);
        ClientHandler.getInstance().outputToClients(hubSessionID, true, true, false, forwardRequest);
    }

    // #372 -> #373
    private void receiveGadgetGroups(String[] commands) throws Exception {
        // #372 HUB ->PS -- DONE
        int targetSessionID = Integer.parseInt(commands[1]);

        // #373 PS -> CLIENT -- DONE
        // Encapsulate (build) new command from the de-encapsulated incoming command (according to protocol)
        String forwardGroups = "373";
        for (int command = 2; command < commands.length; command++) {
            forwardGroups = String.format("%s::%s", forwardGroups, commands[command]);
        }
        // Send to individual client
        ClientHandler.getInstance().outputToClients(targetSessionID, false, true, false, forwardGroups);
    }

    // ===================================== DEBUG LOGS =======================================================

    public void debugLog(String log, String... data) {
        synchronized (lock_debugLogs) {
            if (settings.isDebugMode()) {
                String logData = "";
                if (data.length > 0) {
                    logData = String.format("[%s]", data[0]);
                    if (data.length > 1) {
                        for (int i = 1; i < data.length; i++) {
                            logData = String.format("%s %s", logData, data[i]);
                        }
                    }
                }
                log = String.format("%-30s%s", log.concat(":"), logData);
                System.out.println(log.length() > 90 ? log.substring(0, 90).concat("[...]") : log);
            }
        }
    }

    public void debugLog(String log, int threadID, String... data) {
        synchronized (lock_debugLogs) {
            if (settings.isDebugMode()) {
                String logData = "";
                if (data.length > 0) {
                    logData = String.format("[%s] (Session %s)", data[0], threadID);
                    if (data.length > 1) {
                        for (int i = 1; i < data.length; i++) {
                            logData = String.format("%s %s", logData, data[i]);
                        }
                    }
                }
                log = String.format("%-30s%s", log.concat(":"), logData);
                System.out.println(log.length() > 90 ? log.substring(0, 90).concat("[...]") : log);
            }
        }
    }


}
