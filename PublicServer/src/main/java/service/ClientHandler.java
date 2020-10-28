package service;


import web_resource.WebSocketServer;
import org.eclipse.jetty.websocket.api.Session;
import DAO.DB_Clients;
import model.Client;
import model.ClientRequest;
import model.Client_Hub;
import model.Client_User;
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

    public HashMap<Session, Client> connectedClients;


    private int clientLimit;
    private DB_Clients clientDB;
    private final Object lock_clients;
    private final Object lock_login;
    private String encryptedKey;


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
        Spark.threadPool(clientLimit);
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

            // Default idle threshold for not logged in clients
            session.setIdleTimeout(8000);
            //Map session to new generic client instance
            Client newClient = new Client();
            connectedClients.put(session, newClient);
            debugLog("Connected clients", String.valueOf(connectedClients.size()));
        }
    }

    public void removeClient(Session session) {
        synchronized (lock_clients) {
            if (session.isOpen()) {
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
                    if (request.toLowerCase().equals("ping")) {
                        // Ping. Resets idle time
                        debugLog("Ping from client", getIP(session));
                    } else {
                        // Add request to server
                        ClientRequest newRequest = new ClientRequest(connectedClients.get(session).sessionID, request);
                        Server.getInstance().clientRequests.put(newRequest);
                    }
                } else {
                    session.setIdleTimeout(60 * 1000); // Increase idle threshold
                    // ****if the client is not logged in call the login****
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
                writeToClient(session, "903::".concat(e.getMessage()));
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

        // encrypt the sessionKey and then send the generated encrypted key to the DB
        // encryptedKey = String.valueOf(Encryption.encrypt(newSessionKey, String.valueOf(generateSalt(160))));
        //System.out.println(newSessionKey+"<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< new");


        //Try to log in with nameID and password (throws exception on invalid)
        JSONObject result = clientDB.manualUserLogin(nameID, pwd, newSessionKey);
        int hubID = (Integer) result.get("hubId");
        boolean admin = (Boolean) result.get("isAdmin");

        // check the hub if it's connected, get the hub alias, if not throw an exception
        //String hubAlias = getHubByHubID(hubID).alias;
        String hubAlias = getHubAlias(hubID);

        // Create valid user instance
        Client_User validClient = new Client_User(hubID, nameID, admin);

        // Overwrite the Client mapped to the session, with a specialized and logged in:
        connectedClients.put(session, validClient);

        debugLog(String.format("%s (%s)", "Client logged in", nameID), validClient.sessionID, getIP(session));

        // Response according to HoSo protocol #102
        String loginConfirmation = String.format("102::%s::%s::%s::%s", nameID, admin, hubAlias, newSessionKey);
        writeToClient(session, loginConfirmation);
        //302 from the client to the server, 302 from the server to the hub, 303 from hub to server, 304 from server to client
        // Request all gadgets from the hub that belongs to the client on behalf of the client
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
        // Request according to HoSo protocol: #103
        String nameID = loginRequest[1];
        String sessionKey = loginRequest[2];

        // Here it should verify the entered session key with one that has been encrypted using the same encrypted key

        //boolean check = Encryption.verifyValue(sessionKey,encryptedKey, String.valueOf(generateSalt(160)));
        //System.out.println(encryptedKey+"<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< valid");
        //System.out.println(check+"<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< valid");
        //if (check) {

        JSONObject result = clientDB.automaticUserLogin(nameID, sessionKey);
        int hubId = (Integer) result.get("hubId");
        boolean isAdmin = (Boolean) result.get("isAdmin");

        // Here it should be checked if the client is connected to its hub
        //String hubAlias = getHubByHubID(hubId).alias;
        getHubAlias(hubId);

        Client_User validClient = new Client_User(hubId, nameID, isAdmin);
        connectedClients.put(session, validClient);

        debugLog(String.format("%s (%s)", "Client logged in", nameID), validClient.sessionID, getIP(session));

        // Response according to HoSo protocol #104
        String responseMsg = "Successful login";
        String loginConfirmation = String.format("104::%s", responseMsg);
        writeToClient(session, loginConfirmation);

        // Request all gadgets on behalf of the client
        String request = String.format("%s::%s", "302", validClient.sessionID); //302::1
        ClientRequest requestAllGadgets = new ClientRequest(validClient.sessionID, request);// 1,"302::1"
        Server.getInstance().clientRequests.put(requestAllGadgets);
        //}else {
        //  throw new Exception("Wrong session key! ");
        //}
    }

    // #120
    private void hubLogin(Session session, String[] loginRequest) throws Exception {
        //120::12::1234::my house
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
        String msgToHub;
        String hubLoginConfirmation;
        int hubId = Integer.parseInt(loginRequest[1]);
        String hubPass = loginRequest[2];
        String hubAlas = loginRequest[3];
        if (clientDB.hubLogin(hubId, hubPass)) {

            Client_Hub validHub = new Client_Hub(hubId, hubAlas);
            connectedClients.put(session, validHub);
            debugLog(String.format("%s (%s)", "Hub logged in", hubId), validHub.sessionID, getIP(session));
            // response
            msgToHub = "Successful login";
            hubLoginConfirmation = String.format("121::%s", msgToHub);
            writeToClient(session, hubLoginConfirmation);
        } else {
            msgToHub = "Unsuccessful login, the hub information are incorrect!";
            hubLoginConfirmation = String.format("901::%s", msgToHub);
            writeToClient(session, hubLoginConfirmation);
        }
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

    private Client_Hub getHubByHubID(int hubID) throws Exception {

        /*if (hubID <= 1) {
            throw new Exception("successfully logged in, BUT NO hub is connected to your username!!");
        }
        Client_Hub client_hub = new Client_Hub(hubID, "My house");

        return client_hub;*/

        synchronized (lock_clients) {
            for (Session session : connectedClients.keySet()) {
                Client client = connectedClients.get(session);
                if (client instanceof Client_Hub && client.hubID == hubID) {
                    return (Client_Hub) client;
                }
            }
            throw new Exception("Your hub is not connected");
        }
    }

    private String getHubAlias(int hubID) throws Exception {
        synchronized (lock_clients) {
            for (Session session : connectedClients.keySet()) {
                Client client = connectedClients.get(session);
                if (client instanceof Client_Hub && client.hubID == hubID) {
                    return ((Client_Hub) client).alias;
                }
            }
            throw new Exception("Your hub is not connected");
        }
    }

    public int getHubSessionIdByUserSessionId(int userSessionID) throws Exception {
        synchronized (lock_clients) {
            int hubID = connectedClients.get(getSession(userSessionID)).hubID;
            return getHubByHubID(hubID).sessionID;
        }
    }

    private Session getSession(int sessionID) throws Exception {
        synchronized (lock_clients) {
            for (Session session : connectedClients.keySet()) {
                if (connectedClients.get(session).sessionID == sessionID) {
                    return session;
                }
            }
            throw new Exception("No session match");
        }
    }

    // For logging purposes
    private String getIP(Session session) {
        return session.getRemoteAddress().getAddress().toString().substring(1);
    }

    // ======================================== OUTPUT TO CLIENT(S) =================================================
    // Used by Server class to output data to connected clients

    public void outputToClients(int sessionID, boolean toHub, boolean onlyToIndividual, boolean onlyToAdmin, String msg) {
        synchronized (lock_clients) {
            Session targetSession = null;
            try {
                targetSession = getSession(sessionID);

                /*if (onlyToIndividual) {
                    if (toHub) {
                        targetSession = getHubSession(sessionID);// get the hub session
                    } else {
                        targetSession = getSession(sessionID); // get client session here
                    }*/

                if(onlyToIndividual) {
                    Client targetClient = connectedClients.get(targetSession);// I will get the whole client object
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
                debugLog(e.getMessage(), getIP(targetSession), "SessionID: " + sessionID);
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

    private void debugLog(String log, int sessionID, String... data) {
        Server.getInstance().debugLog(log, sessionID, data);
    }
}
