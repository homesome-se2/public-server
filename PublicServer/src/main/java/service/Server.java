package service;

import DAO.DB_Clients;
import com.google.gson.Gson;
import model.ClientRequest;
import model.Client_Hub;
import model.Settings;
import org.eclipse.jetty.websocket.api.Session;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Server {

    public BlockingQueue<ClientRequest> clientRequests;
    public volatile Settings settings;
    public volatile boolean terminateServer;
    private DB_Clients clientDB;

    // config.json
    //Note: 'config.json' should be located "next to" the project folder: [config.json][PublicServer]
    //private static final String configFileJSON = "./config.json";  // When run as JAR on Linux
    // private static final String configFileJSON = (new File(System.getProperty("user.dir")).getParentFile().getPath()).concat("/config.json"); // When run from IDE
    private static final String configFileJSON = "config.json";
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
                // terminate connection with the mock hub
                //mock.close();
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
                            logOutSpecificDevice(commands, sessionID);
                            break;
                        case "106":
                            logOutAllDevices();
                            break;
                        case "201":
                            requestRemoteAccessCredentials(commands, sessionID);
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
                        case "351":
                            detectNewGadget(commands, sessionID);
                            break;
                        case "353":
                            gadgetConnectionLost(commands, sessionID);
                            break;
                        case "370":
                            requestGadgetGroups(sessionID);
                            break;
                        case "372":
                            receiveGadgetGroups(commands);
                            break;
                        case "401":
                            alterGadgetAliasReq(commands, sessionID);
                            break;
                        case "403":
                            reportGadgetAliasChange(commands, sessionID);
                            break;
                        case "410":
                            requestToEditOrCreateGadgetGroup(commands, sessionID);
                            break;
                        case "411":
                            deleteGadgetGroup(commands, sessionID);
                            break;
                        case "501":
                            notLoggedAndroidReportsLocation(commands, sessionID);
                            break;
                        case "502":
                            loggedAndroidReportsLocation(commands, sessionID);
                            break;
                        default:
                            ClientHandler.getInstance().outputToClients(sessionID, false, true, false, "901::Invalid format");
                            break;
                    }
                } catch (Exception e) {
                    throw new Exception("Terminating processRequests()");
                }
            } catch (Exception e) {
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


    // #301 -> #302
    private void userRequestAllHubGadgets(int issuingSessionID) throws Exception {
        String forwardRequest = String.format("302::%s", issuingSessionID);
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(issuingSessionID);
        ClientHandler.getInstance().outputToClients(hubSessionID, true, true, false, forwardRequest);
    }

    // #302 -> #302
    // ps sends 302 to the hub to get all gadgets
    private void serverRequestAllHubGadgets(String[] commands, int issuinSessionID) throws Exception {

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
        Session session = main.java.service.ClientHandler.getInstance().getSession(cSessionID);
        Client theClient = main.java.service.ClientHandler.getInstance().getConnectedClients().get(session);
        int hubID = theClient.hubID;
        String forwardGadgetsMsg = String.format("%s::%s::%s", "312", gadgetID, newGadgetState);
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
    private void requestGadgetGroups(int cSessionID) throws Exception {
       /*
        // #370 CLIENT -> PS---- NO ARGUMENTS      ---- DONE
        // #371 PS -> HUB ------ CLIENT SESSION ID ---- DONE
        // #372 HUB ->PS ------- CLIENT SESSION ID && [groupName]:[G_id]:[G_id]:[G_id]::[groupName]:[G_id]:[G_id] --- DONE
        // #373 PS -> CLIENT --- [groupName]:[G_id]:[G_id]:[G_id]::[groupName]:[G_id]:[G_id]

        // #371 PS -> HUB ---- DONE
        Session session = null;
        try {
            String forwardRequest = String.format("%s::%s", "371", cSessionID);
            session = main.java.service.ClientHandler.getInstance().getSession(cSessionID);
            Client theClient = main.java.service.ClientHandler.getInstance().getConnectedClients().get(session);
            int hubID = theClient.hubID;
            main.java.service.ClientHandler.getInstance().outputToClients(hubID, true, true, false, forwardRequest);
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

    // TODO: implement
    // C -> PS 105 , 106
    private void logOutSpecificDevice(String[] commands, int issuinSessionID) {
        // destroys client session key for current session

        //TODO: Implement
        // User client (Android/browser) has manually pressed the logout button.
        // Remove/overwrite the client's sessionKey in DB. This would force a manual login next time client wants to connect.
        // This method returns nothing (possibly just an exception msg '901::xxxx' if something goes wrong).
        // how should I get name of the cient in order to delete his session key ???
        //clientDB.manualUserLogout();
    }

    private void logOutAllDevices() {
        // destroys client session key for all recorded session

    }


    //WC -> PS 201 -------{ UNDER CONSTRUCTION }----------
    private void requestRemoteAccessCredentials(String[] commands, int issuinSessionID) throws Exception {
        // @params  C_nameID, C_Pwd
        String requestedNameID = commands[1];
        String requestedPwd = commands[2];
        int theClientHubSession = ClientHandler.getInstance().getHubSessionIdByUserSessionId(issuinSessionID);
        Client_Hub theCustomerHub = ClientHandler.getInstance().getHubBySessionID(theClientHubSession);

        //PS -> WC 202  --- returnNewAccessCredentials
        // return the ->   hubID - hubPwd - client nameID to the WC
        String hubID = String.valueOf(theCustomerHub.hubID);
        String hubPwd = "";
        String cNameID = "";
        String forwardMsg = String.format("%s::%s::%s::%s", "202", hubID, hubPwd, cNameID);

    }

    // H ->PS 351 -- PS -> C 352
    private void detectNewGadget(String[] commands, int issuingSessionID) {
        //351::[G1_id]::[G1_alias]::[G1_type]::[G1_valueTemplate]::[G1_state]::[G1_pollDelaySec]
        //PS -> C 352 out put to client ---- forwardTheNewDetectedGadget
        String forwardGadgetsMsg = String.format("%s", "352");
        for (int command = 1; command < commands.length; command++) {
            forwardGadgetsMsg = String.format("%s::%s", forwardGadgetsMsg, commands[command]);
        }
        // Send to all clients who are connected to that hub
        ClientHandler.getInstance().outputToClients(issuingSessionID, false, false, false, forwardGadgetsMsg);
    }


    //H-> PS 353 -- PS -> C 354
    private void gadgetConnectionLost(String[] commands, int issuingSessionID) {
        String gadgetID = commands[1];

        //PS -> C 354 gadgetRemovalReq
        String forwardMsg = String.format("%s::%s", "354", gadgetID);
        // Send to all users associated with that hub -> th connection between the hub and the clients are figured by outputToAllClients()
        ClientHandler.getInstance().outputToClients(issuingSessionID, false, false, false, forwardMsg);

    }

    //WC -> PS 401 -- PS -> H 402
    private void alterGadgetAliasReq(String[] commands, int issuingSessionID) throws Exception {
        // @params  g_ID, g_newAlias
        String gadgetID = commands[1];
        String newAlias = commands[2];

        //PS -> H 402 reqAlterGadgetAlias
        // @params  C_sessionId,g_ID, g_newAlias
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(issuingSessionID);
        String forwardMsg = String.format("%s::%s::%s::%s", "402", issuingSessionID, gadgetID, newAlias);
        // Sending to the hub that belongs to that client who issued the alter gadget alias request
        ClientHandler.getInstance().outputToClients(hubSessionID, true, false, false, forwardMsg);
    }

    //403 H-> PS reportGadget alias change H-> PS G_ID - G_newAlias
    //404 PS -> C @params G_ID ,G_newAlias
    private void reportGadgetAliasChange(String[] commands, int issuingSessionID) {
        String gadgetID = commands[1];
        String newAlias = commands[2];

        String forwardMsg = String.format("%s::%s::%s", "404", gadgetID, newAlias);
        ClientHandler.getInstance().outputToClients(issuingSessionID, false, false, false, forwardMsg);
    }

    //410 WC -> PS
    private void requestToEditOrCreateGadgetGroup(String[] commands, int issuingSessionID) {
        //@params A3 = [groupName]:[G_id]:[G_id]:[G_id]
        // if groupName Exist then edit else create new group
        String groupName = commands[1];

        //we should include a protocol that will talk with the hub here to inform about the name of the group
        String forwardGadgetsMsg = String.format("%s::", "...");
        for (int command = 2; command < commands.length; command++) {
            forwardGadgetsMsg = String.format("%s:%s", forwardGadgetsMsg, commands[command]);
        }

    }

    //411 WC -> PS @params groupName
    private void deleteGadgetGroup(String[] commands, int issuingSessionID) {
        String groupName = commands[1];
    }

    //501 AC -> PS --- 503 PS -> H
    public void notLoggedAndroidReportsLocation(String[] commands, int issuingSessionID) throws Exception {
        //C_nameID, C_sessionKey, Ac_longitude, Ac_latitude
        String nameID = commands[1];
        String sessionKey = commands[2];
        String longitude = commands[3];
        String latitude = commands[4];
        Session session = ClientHandler.getInstance().getSession(issuingSessionID);

        // call automatic login ang give it the session key
        // we should change the method of automatic login
        String loginRequest = "103::" + nameID + "::" + sessionKey;
        ClientHandler.getInstance().addClientRequest(session, loginRequest);
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(issuingSessionID);
        //503 PS -> H
        //forward C_nameID, Ac_longitude, Ac_latitude
        String forwardMsg = String.format("%s::%s::%s::%s", "503", nameID, longitude, latitude);
        ClientHandler.getInstance().outputToClients(hubSessionID, true, false, false, forwardMsg);

    }

    //502 AC -> PS -- 503 PS -> H
    public void loggedAndroidReportsLocation(String[] commands, int issuingSessionID) throws Exception {
        //Ac_longitude, Ac_latitude
        String longitude = commands[1];
        String latitude = commands[2];

        //503 PS -> H
        //forward C_nameID, Ac_longitude, Ac_latitude
        int hubSessionID = ClientHandler.getInstance().getHubSessionIdByUserSessionId(issuingSessionID);
        String forwardMsg = String.format("%s::%s::%s", "503", longitude, latitude);
        ClientHandler.getInstance().outputToClients(hubSessionID, true, false, false, forwardMsg);
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
