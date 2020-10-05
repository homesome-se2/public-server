package service;

import model.ClientRequest;
import model.Settings;
import temp_mock.Mock_Interaction;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Server {

    public BlockingQueue<ClientRequest> clientRequests;
    public volatile Settings settings;
    public volatile boolean terminateServer;

    // Lock objects
    private final Object lock_closeServer;
    private final Object lock_debugLogs;

    // Make Singleton
    private static Server instance = null;

    // Temporary mock
    private Mock_Interaction mock; // REMOVE LATER

    public static Server getInstance() {
        if (instance == null) {
            instance = new Server();
        }
        return instance;
    }

    private Server() {
        clientRequests = new ArrayBlockingQueue<>(10);
        terminateServer = false;
        mock = new Mock_Interaction();
        lock_closeServer = new Object();
        lock_debugLogs = new Object();
    }

    public void launch() {
        System.out.println("HomeSome server running...");
        try {
            // Read in settings from JSON
            settings = new Settings();
            settings.readInSettings();
            mock.launch();

            // Launch ClientHandler
            ClientHandler.getInstance().launch(settings.getServerPort(), settings.getServerThreadPool());

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
                mock.close();
                ClientHandler.getInstance().close();
                System.out.println("HomeSome server shutting down");
            }
        }
    }

    //================================ PROCESS CLIENT REQUESTS ==============================================

    // Executed by worker thread: processRequestsThread
    private void processRequests() throws Exception {
        while (!terminateServer) {
            try {
                ClientRequest clientRequest = clientRequests.take();
                String commands[] = clientRequest.request.split("::");
                int issuingThreadID = clientRequest.threadID;

                switch (commands[0]) {
                    case "105":
                        clientLogout(commands, issuingThreadID);
                    case "302":
                        requestAllHubGadgets(commands, issuingThreadID);
                        break;
                    case "303":
                        receiveAllHubGadgets(commands, issuingThreadID);
                        break;
                    case "311":
                        requestGadgetStateChange(commands, issuingThreadID);
                        break;
                    case "315":
                        receiveGadgetStateChange(commands, issuingThreadID);
                        break;
                    default:
                        ClientHandler.getInstance().outputToGenericClient("901::Invalid format", issuingThreadID);
                        break;
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
     *
     * #303 -> #304  : The incoming request is a #303 request, and the forwarding request should be a #304 command.
     * #302 -> #302  : The forwarding is the same command (#302 -> #302)
     * #105 -> X     : No forwarding to be done.
     */

    // #105 -> X
    private void clientLogout(String[] commands, int issuingThreadID) {
        //TODO: Implement
        // User client (Android/browser) has manually pressed the logout button.
        // Remove/overwrite the client's sessionKey in DB. This would force a manual login next time client wants to connect.
        // This method returns nothing (possibly just an exception msg '901::xxxx' if something goes wrong).
    }


    // #302 -> #302
    private void requestAllHubGadgets(String[] commands, int issuingThreadID) throws Exception {
        //TODO: Implement (and remove mock)
        // This method will be used when a newly logged in user requests all gadget data from its associated hub.
        // It is actually called from ClientHandler, but the request goes to the hub of which the newly logged in client belongs.
        // The msg is forwarded to the target hub as it is (no inspection or appending needed).

        mock.hubReportsAllGadgets(issuingThreadID); // REMOVE LATER
    }

    // #303 -> #304
    private void receiveAllHubGadgets(String[] commands, int issuingThreadID) throws Exception {
        int targetThread = Integer.parseInt(commands[1]);

        // Encapsulate (build) new command from the decapsulated incoming command (according to protocol)
        String forwardGadgetsMsg = "304";
        for (int command = 2 ; command < commands.length ; command++) {
            forwardGadgetsMsg = String.format("%s::%s", forwardGadgetsMsg, commands[command]);
        }
        // Send to individual client
        ClientHandler.getInstance().outputToUsers(forwardGadgetsMsg, targetThread, true, false);
    }

    // #311 -> #312
    private void requestGadgetStateChange(String[] commands, int issuingThreadID) throws Exception {
        //TODO: Implement
        // Client requests to alter a gadget state.
        // Rebuild and forward the request to target hub as: #312
        // Look in ClientManager for appropriate method to use to locate the hub threadID based on Client's hubID.
        // Note that the client's thread ID should be included in the forwarded msg to the hub.
    }

    // #315 -> #316
    private void receiveGadgetStateChange(String[] commands, int issuingThreadID) throws Exception {
        // Rebuild and forward the update to all users associated with that hub
        String gadgetID = commands[1];
        String newState = commands[2];
        String forwardMsg = String.format("%s::%s::%s", "316", gadgetID, newState);
        // Send to all users associated with that hub
        ClientHandler.getInstance().outputToUsers(forwardMsg, issuingThreadID, false, false);
    }

    // ===================================== DEBUG LOGS =======================================================

    public void debugLog(String log, String... data) {
        synchronized (lock_debugLogs) {
            if(settings.isDebugMode()) {
                String logData = "";
                if(data.length > 0) {
                    logData = String.format("[%s]", data[0]);
                    if(data.length > 1 ) {
                        for(int i = 1 ; i < data.length; i++) {
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
            if(settings.isDebugMode()) {
                String logData = "";
                if(data.length > 0) {
                    logData = String.format("[%s] (Thread %s)", data[0], threadID);
                    if(data.length > 1 ) {
                        for(int i = 1 ; i < data.length; i++) {
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
