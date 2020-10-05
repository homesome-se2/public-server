package service;

import DAO.DB_Clients;
import model.Client;
import model.ClientRequest;
import model.Client_Hub;
import model.Client_User;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler {

    /**
     * Responsible for:
     * - Accepting new clients and launching them on ClientThreads
     * - Process login requests issued by ClientThreads
     * - Keep record of all clients currently connected to the system.
     * - The only class accessing the database class DB_Clients.
     */

    private HashMap<Integer, Client_User> connectedUsers;
    private HashMap<Integer, Client_Hub> connectedHubs;

    private int serverTcpPort;
    private int threadPoolLimit;
    private ServerSocket serverSocket;
    private final Object lock_acceptClients;
    private final Object lock_login;
    private final Object lock_connectedUsers;
    private final Object lock_connectedHubs;

    // Worker thread
    private Thread acceptClientsThread;

    // Make Singleton
    private static ClientHandler instance = null;

    public static ClientHandler getInstance() {
        if (instance == null) {
            instance = new ClientHandler();
        }
        return instance;
    }

    private ClientHandler() {
        connectedUsers = new HashMap<>();
        connectedHubs = new HashMap<>();
        serverSocket = null;
        lock_acceptClients = new Object();
        lock_login = new Object();
        lock_connectedUsers = new Object();
        lock_connectedHubs = new Object();
        acceptClientsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    acceptClientConnections();
                } catch (Exception e) {
                    close();
                }
            }
        });
    }

    public void launch(int serverTcpPort, int threadPoolLimit) {
        this.serverTcpPort = serverTcpPort;
        this.threadPoolLimit = threadPoolLimit;
        acceptClientsThread.start();
    }

    public void close() {
        try {
            // Write terminate msg to all client threads
            synchronized (lock_connectedUsers) {
                for (int key : connectedUsers.keySet()) {
                    connectedUsers.get(key).addToOutputQueue("exit");
                }
            }
            synchronized (lock_connectedHubs) {
                for (int key : connectedHubs.keySet()) {
                    connectedHubs.get(key).addToOutputQueue("exit");
                }
            }

            if (serverSocket != null) {
                serverSocket.close();
            }
            System.out.println("ClientHandler shutting down all threads");
        } catch (IOException e) {
            System.out.println("Unable to close serverSocket");
        }
    }

    // ======================================== ACCEPT NEW CLIENTS =================================================

    private void acceptClientConnections() throws Exception {
        synchronized (lock_acceptClients) {

            // Thread pool to manage ClientThreads.
            ExecutorService executor = Executors.newFixedThreadPool(threadPoolLimit);

            serverSocket = new ServerSocket(serverTcpPort);

            int threadID = 0;

            while (!Server.getInstance().terminateServer) {

                Socket clientConnection = null;

                try {
                    // Receive client connection requests
                    clientConnection = serverSocket.accept();

                    // Assigning new thread for client
                    executor.submit(new ClientThread(++threadID, clientConnection));

                } catch (Exception e) {
                    if (clientConnection != null) {
                        clientConnection.close();
                    }
                    System.out.println("Exception in acceptClients");
                }
            }
            // Hard shutdown. System terminating.
            executor.shutdownNow();
        }
    }

    // ========================================== CLIENT LOGIN ===================================================

    // Process client login requests: Called from ClientThread before gaining access to server features.
    public void login(int threadID, String ip, BlockingQueue<String> outputQueue, String loginRequest) throws Exception {
        synchronized (lock_login) {
            try {

                //Server.getInstance().debugLog("Client connection request", loginRequest);

                String[] commands = loginRequest.split("::");

                switch (commands[0]) {
                    case "101": // Manual user login (Android or browser)
                        manualUserLogin(ip, outputQueue, threadID, commands);
                        break;
                    case "103": // Automatic user login (Android or browser)
                        automaticUserLogin(ip, outputQueue, threadID, commands);
                        break;
                    case "120": // Hub login
                        hubLogin(ip, outputQueue, threadID, commands);
                        break;
                    default:
                        throw new Exception("Invalid login format");
                }
            } catch (Exception e) {
                debugLog("Invalid login format", threadID, ip);
                outputQueue.put("901::".concat(e.getMessage()));
                outputQueue.put("exit");
            }
        }
    }

    // #101
    private void manualUserLogin(String ip, BlockingQueue<String> outputQueue, int threadID, String[] loginRequest) throws Exception {
        try {
            // Request according to HoSo protocol: #101
            String nameID = loginRequest[1];
            String pwd = loginRequest[2];

            // Generate new session key to use henceforth if this login succeeds.
            String newSessionKey = generateSessionKey(nameID);

            //Try to log in with nameID and password (throws exception on invalid)
            JSONObject result = DB_Clients.getInstance().manualUserLogin(nameID, pwd, newSessionKey);
            int hubID = (Integer) result.get("hubID");
            boolean admin = (Boolean) result.get("admin");

            // If hub not connected: Throws exception + msg: "Hub not connected"
            //String hubAlias = getHubByHubID(hubID).alias;   --> Uncomment when home server(hubs) are available (or use mock)
            String hubAlias = "My haaouse"; // REMOVE LATER WHEN ABOVE LINE CAN BE USED = When a hub is connected

            // Create valid user instance
            Client_User validClient = new Client_User(hubID, ip, outputQueue, nameID, admin);

            // Add user to register of connected clients
            addConnectedUser(threadID, validClient);

            debugLog(String.format("%s (%s)", "Client logged in", nameID), threadID, ip);

            // Response according to HoSo protocol #102
            String loginConfirmation = String.format("102::%s::%s::%s::%s", nameID, admin, hubAlias, newSessionKey);
            outputQueue.put(loginConfirmation);

            // Request all gadgets on behalf of the client
            String request = String.format("%s::%s", "302", threadID);
            ClientRequest requestAllGadgets = new ClientRequest(threadID, request);
            Server.getInstance().clientRequests.put(requestAllGadgets);
        } catch (Exception e) {
            // Pass custom exception msg from DB_Clients to client
            String clientOutput = String.format("901::%s", e.getMessage());
            outputQueue.put(clientOutput);
            // Terminate ClientThread
            outputQueue.put("exit");

            debugLog("Client failed to login", threadID, ip, clientOutput);
        }
    }

    // #103
    private void automaticUserLogin(String ip, BlockingQueue<String> outputQueue, int threadID, String[] loginRequest) throws Exception {
        try {
            //TODO: Implement automatic login
            /**
             * Similar to manualUserLogin, except:
             * - Login credentials from client is: nameID and sessionKey
             * - No new sessionKey is stored in DB, or returned to client
             * - Response to client upon successful login: #104
             */

        } catch (Exception e) {
            // Pass custom exception msg from DB_Clients to client
        }
    }

    // #120
    private void hubLogin(String ip, BlockingQueue<String> outputQueue, int threadID, String[] loginRequest) throws Exception {
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

    // ========================================== MANAGE CLIENTS ====================================================

    // Called upon successful user login
    public void addConnectedUser(int threadID, Client_User client) {
        synchronized (lock_connectedUsers) {
            connectedUsers.put(threadID, client);
            debugLog("Users logged in", String.valueOf(connectedUsers.size()));
        }
    }

    // Called upon successful hub login
    public void addConnectedHub(int threadID, Client_Hub client) {
        synchronized (lock_connectedUsers) {
            connectedHubs.put(threadID, client);
        }
    }

    // Called from ClientThread when a ClientThread is terminating.
    public void removeConnectedClient(int threadID) throws Exception {
        synchronized (lock_connectedUsers) {
            if (connectedUsers.containsKey(threadID)) {
                connectedUsers.remove(threadID);
                debugLog("Users logged in", String.valueOf(connectedUsers.size()));
            } else {
                synchronized (lock_connectedHubs) {
                    if (connectedHubs.containsKey(threadID)) {
                        connectedHubs.remove(threadID);
                        debugLog("Hubs logged in", String.valueOf(connectedUsers.size()));
                    }
                }
            }
        }
    }

    public Client_User getUser(int threadID) throws Exception {
        synchronized (lock_connectedUsers) {
            return connectedUsers.get(threadID);
        }
    }

    //TODO: ONLY USED BY MOCK CLASS: Remove later.
    public Integer[] getAllUserThreadIDs() throws Exception {
        synchronized (lock_connectedUsers) {
            return connectedUsers.keySet().toArray(new Integer[connectedUsers.size()]);
        }
    }

    public Client_Hub getHub(int threadID) throws Exception {
        synchronized (lock_connectedUsers) {
            return connectedHubs.get(threadID);
        }
    }

    public Client_Hub getHubByHubID(int hubID) throws Exception {
        synchronized (lock_connectedHubs) {
            for (int thread : connectedHubs.keySet()) {
                if (connectedHubs.get(thread).hubID == hubID) {
                    return connectedHubs.get(thread);
                }
            }
            throw new Exception("Hub not connected");
        }
    }

    // Return client that is either a Client_Hub or Client_User
    public Client getGenericClient(int threadID) throws Exception {
        synchronized (lock_connectedUsers) {
            if (connectedUsers.containsKey(threadID)) {
                return connectedUsers.get(threadID);
            } else {
                synchronized (lock_connectedHubs) {
                    return connectedHubs.get(threadID);
                }
            }
        }
    }

    // ======================================== OUTPUT TO CLIENT(S) =================================================
    // Used by Server class to output data to connected clients

    public void outputToUsers(String msg, int threadID, boolean onlyToIndividual, boolean onlyToAdmin) {
        synchronized (lock_connectedUsers) {
            if (connectedUsers.containsKey(threadID)) {
                if (onlyToIndividual) {
                    // Msg to individual user
                    if (!onlyToAdmin || connectedUsers.get(threadID).isAdmin()) {
                        connectedUsers.get(threadID).addToOutputQueue(msg);
                    }

                } else {
                    // Msg to all users belonging to the same hub
                    int hubID = connectedUsers.get(threadID).hubID;
                    for (int thread : connectedUsers.keySet()) {
                        Client_User client = connectedUsers.get(thread);
                        if (client.hubID == hubID && (!onlyToAdmin || client.isAdmin())) {
                            client.addToOutputQueue(msg);
                        }
                    }
                }
            }
        }
    }

    public void outputToHub(String msg, int threadID) {
        synchronized (lock_connectedHubs) {
            connectedHubs.get(threadID).addToOutputQueue(msg);
        }
    }

    // When client specialization is unknown, e.g. when passing an exception msg.
    public void outputToGenericClient(String msg, int threadID) {
        synchronized (lock_connectedUsers) {
            if (connectedUsers.containsKey(threadID)) {
                connectedUsers.get(threadID).addToOutputQueue(msg);
            } else {
                synchronized (lock_connectedHubs) {
                    connectedHubs.get(threadID).addToOutputQueue(msg);
                }
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
