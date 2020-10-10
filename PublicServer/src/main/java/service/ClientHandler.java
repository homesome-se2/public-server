package main.java.service;


import main.java.web_resource.WebSocketServer;
import org.eclipse.jetty.websocket.api.Session;
import main.java.DAO.DB_Clients;
import main.java.model.Client;
import main.java.model.ClientRequest;
import main.java.model.Client_Hub;
import main.java.model.Client_User;
import spark.Spark;


import org.json.simple.JSONObject;


import java.io.IOException;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;


public class ClientHandler {

    /**
     * Responsible for:
     * - Accepting new clients and launching them on ClientThreads
     * - Process login requests issued by ClientThreads
     * - Keep record of all clients currently connected to the system.
     * - The only class accessing the database class DB_Clients.
     */

    private HashMap<Session, Client> connectedClients;

    private int clientLimit;
    private DB_Clients clientDB;
    private final Object lock_clients;
    private final Object lock_login;



    // Make Singleton
    private static ClientHandler instance = null;

    public static ClientHandler getInstance() {
        if (instance == null) {
            instance = new ClientHandler();
        }
        return instance;
    }

    private ClientHandler() {
        connectedClients = new HashMap<>();
        clientDB = new DB_Clients();
        lock_clients = new Object();
        lock_login = new Object();
    }

    public void launchWebSocketServer(int serverTcpPort, int clientLimit) {
        this.clientLimit = clientLimit;

        // Create web socket listening on a path, and being implemented by a class.
        Spark.webSocket("/homesome", WebSocketServer.class);
        Spark.port(serverTcpPort);
        Spark.init();
        // Browser test: http://localhost:tcpPort/
        // If no web page is provided, should say "404 Error, com.homesome.service powered by Jetty"

        System.out.println("Jetty WebSocket (web) server started");
    }

    public void stopWebSocketServer() {
        Spark.stop();
    }



    // ======================================== ACCEPT AND MANAGE NEW CLIENTS =================================================

    public void addClient(Session session) {
        synchronized (lock_clients) {

            if (connectedClients.size() <= clientLimit) {
                Client newClient = new Client();
                connectedClients.put(session, newClient);
            } else {
                System.out.println("Client limit reached.");
                if(session.isOpen()) {
                    session.close();
                }
            }
            debugLog("Connected clients", String.valueOf(connectedClients.size()));
        }
    }

    public void removeClient(Session session) {
        synchronized (lock_clients) {
            if(session.isOpen()) {
                session.close();
            }
            connectedClients.remove(session);
            debugLog("Connected clients", String.valueOf(connectedClients.size()));
        }
    }

    // ========================================= CLIENT REQUESTS ==================================================

    // Called from WebSocket implementation class @OnWebSocketMessage
    public void addClientRequest(Session session, String request) {
        synchronized (lock_clients) {
            debugLog("Request from client", getIP(session), request);
            try {
                if (connectedClients.get(session).loggedIn) {
                    // Add request to server
                    ClientRequest newRequest = new ClientRequest(connectedClients.get(session).sessionID, request);
                    Server.getInstance().clientRequests.put(newRequest);
                } else {
                    clientLogin(session, request);
                }
            } catch (Exception e) {
                debugLog("Unable to handle request", getIP(session), request);
            }
        }
    }

    // ========================================== CLIENT LOGIN ===================================================

    // Process client login requests: Called from ClientThread before gaining access to server features.
    private void clientLogin(Session session, String loginRequest) {
        synchronized (lock_login) {
            try {

                String[] commands = loginRequest.split("::");

                switch (commands[0]) {
                    case "101": // Manual user login (Android or browser)
                        manualUserLogin(session, commands);
                        break;
                    case "103": // Automatic user login (Android or browser)
                        automaticUserLogin(session, commands);
                        break;
                    case "120": // Hub login
                        hubLogin(session, commands);
                        break;
                    default:
                        throw new Exception("Invalid login format");
                }
            } catch (Exception e) {
                debugLog("Failed login", getIP(session), e.getMessage());
                // Pass custom exception msg. E.g. from DB_Clients
                writeToClient(session, "901::".concat(e.getMessage()));
                // session.close();
                removeClient(session);
            }
        }
    }

    // #101
    private void manualUserLogin(Session session, String[] loginRequest) throws Exception {
        // Request according to HoSo protocol: #101
        String nameID = loginRequest[1];
        String pwd = loginRequest[2];

        // Generate new session key to use henceforth if this login succeeds.
        String newSessionKey = generateSessionKey(nameID);

        //Try to log in with nameID and password (throws exception on invalid)
        JSONObject result = clientDB.manualUserLogin(nameID, pwd, newSessionKey);
        int hubID = (Integer) result.get("hubID");
        boolean admin = (Boolean) result.get("admin");

        // If hub not connected: Throws exception + msg: "Hub not connected"
        //String hubAlias = getHubByHubID(hubID).alias;   --> Uncomment when home server(hubs) are available (or use mock)
        String hubAlias = "My haaouse"; //TODO: REMOVE LATER WHEN ABOVE LINE CAN BE USED = When a hub is connected

        // Create valid user instance
        Client_User validClient = new Client_User(hubID, nameID, admin);

        // Overwrite the Client mapped to the session, with a specialized and logged in:
        connectedClients.put(session, validClient);

        debugLog(String.format("%s (%s)", "Client logged in", nameID), validClient.sessionID, getIP(session));

        // Response according to HoSo protocol #102
        String loginConfirmation = String.format("102::%s::%s::%s::%s", nameID, admin, hubAlias, newSessionKey);
        writeToClient(session, loginConfirmation);

        // Request all gadgets on behalf of the client
        String request = String.format("%s::%s", "302", validClient.sessionID);
        ClientRequest requestAllGadgets = new ClientRequest(validClient.sessionID, request);
        Server.getInstance().clientRequests.put(requestAllGadgets);
    }

    // #103

    private void automaticUserLogin(Session session, String[] loginRequest) throws Exception {
        //TODO: Implement automatic login
        /**
         * Similar to manualUserLogin, except:
         * - Login credentials from client is: nameID and sessionKey
         * - No new sessionKey is stored in DB, or returned to client
         * - Response to client upon successful login: #104
         */
    }

    // #120

    private void hubLogin(Session session, String[] loginRequest) throws Exception {
        //TODO: Implement hub login
        /**
         * - DB method only returns true/false (no data in case of success, and no exception in case of failure)
         * - If successful hub login:
         *   - Create valid hub instance
         *   - Add hub instance to register of connected hubs
         *   - Send login confirmation to hub: #121
         * - If not successful login:
         *   - Same as with failed userLogin
         */
    }

    private String generateSessionKey(String userName) throws Exception {
        // Create "random" hash value with small collision risk.
        // Called by manualUserLogin()
        MessageDigest md = MessageDigest.getInstance("MD5");

        //Generate a string to hash: user name + current time
        String dataString = String.format("%s%s", userName, (new SimpleDateFormat("HHmmss").format(new Date())));

        byte[] data = dataString.getBytes();
        String myChecksum = "";

        md.update(data);
        for (byte b : md.digest()) {                  // ... for each byte read
            myChecksum += String.format("%02X", b);   // ... add it as a hexadecimal number to the checksum
        }
        return myChecksum;
    }

    //TODO: Maybe add a method to hash passwords and sessionKeys before they are stored to DB.

    // ============================================ UTILITIES =======================================================

    // For logging purposes
    private String getIP (Session session) {
        return session.getRemoteAddress().getAddress().toString().substring(1);
    }

    private Session getSession (int sessionID) throws Exception {
        synchronized (lock_clients) {
            for(Session session : connectedClients.keySet()) {
                if(connectedClients.get(session).sessionID == sessionID) {
                    return session;
                }
            }
        }
        throw new Exception("No session match");
    }

    //TODO: ONLY USED BY MOCK. REMOVE LATER
    public int getSessionID() {
        synchronized (lock_clients) {
            if (connectedClients.size() == 0) {
                return -1;
            } else {
                // Find session id of any logged in client.
                return connectedClients.get(connectedClients.entrySet().iterator().next().getKey()).sessionID;
            }
        }
    }

    // ======================================== OUTPUT TO CLIENT(S) =================================================
    // Used by Server class to output data to connected clients

    public void outputToClients(int sessionID, boolean toHub, boolean onlyToIndividual, boolean onlyToAdmin, String msg) {
        synchronized (lock_clients) {
            try {
                if (onlyToIndividual) {
                    Session targetSession = getSession(sessionID);
                    Client targetClient = connectedClients.get(targetSession);
                    // check if user is slogged in
                    if (targetClient.loggedIn) {
                        // check if target is a hub...
                        if ((toHub && targetClient instanceof Client_Hub) ||
                                // ... or target is a user, and verify admin rights in relation to the output request
                                (!toHub && targetClient instanceof Client_User && (!onlyToAdmin || ((Client_User) targetClient).isAdmin()))) {
                            // output to client
                            writeToClient(targetSession, msg);
                        }
                    }
                } else {
                    // Msg to all users belonging to the same hub (note: this is not output to hubs)
                    int hubID = connectedClients.get(getSession(sessionID)).hubID;
                    for (Session session : connectedClients.keySet()) {
                        Client targetClient = connectedClients.get(session);
                        if (targetClient.loggedIn && targetClient.hubID == hubID && targetClient instanceof Client_User) {
                            if (!onlyToAdmin || ((Client_User) targetClient).isAdmin()) {
                                writeToClient(session, msg);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                debugLog(e.getMessage(), sessionID);
            }
        }
    }

    private void writeToClient(Session session, String msg) {
        synchronized (lock_clients) {
            if (session.isOpen()) {
                try {
                    debugLog("Output to client", getIP(session), msg);
                    session.getRemote().sendString(msg);
                } catch (IOException e) {
                    debugLog("Unable to write to client", getIP(session), msg);
                }
            } else {
                debugLog("Client session closed", getIP(session));
            }
        }
    }
    // ===================================== DEBUG LOGS =======================================================


    private void debugLog(String log, String... data) {
        Server.getInstance().debugLog(log, data);
    }

    private void debugLog(String log, int threadID, String... data) {
        Server.getInstance().debugLog(log, threadID, data);
    }


}
