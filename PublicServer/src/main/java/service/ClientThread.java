package service;

import model.ClientRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class ClientThread extends Thread {

    /**
     *
     * Main responsibilities
     * - Issue login process (must be valid to proceed)
     * - Communication with client
     *   - Input:  Add to class Server's queue 'clientRequestsQueue'
     *   - Output: Take from client specific queue 'outputQueue'
     *
     * {@link #login()}
     * Client may be either Client_User or Client_Hub
     *
     * Before gaining access to the the input loop (and hence; the server features),
     * the client thread assures that
     * - Client provides valid login data (and obeys to communication protocol when doing so)
     * If not; thread cancels and removes client from clientList
     * Connections are valid if the client presents either:
     * - User: Valid username and password (manual login)
     * - User: Valid username and key (automatic login)
     * - Hub:  Valid hubID and password
     */

    private final int threadID;
    private Socket clientConnection;
    private volatile BufferedReader input = null;
    private volatile PrintWriter output = null;

    private BlockingQueue<String> outputQueue;
    private Thread outputThread;
    private volatile boolean terminateThread;
    private final Object lock_closeRes;


    public ClientThread(int threadID, Socket clientConnection) {
        this.threadID = threadID;
        this.clientConnection = clientConnection;
        terminateThread = false;
        lock_closeRes = new Object();
        outputThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    outputToClient();
                } catch(Exception e) {
                    closeThread();
                }
            }
        });
    }

    @Override
    public void run() {
        try {
            Server.getInstance().debugLog("New ClientThread", threadID, getClientIP());

            // obtaining input and output streams
            input = new BufferedReader(new InputStreamReader(clientConnection.getInputStream()));
            output = new PrintWriter(clientConnection.getOutputStream(), true);

            // Initiate the queue from which the ClientThread will derive what to output to the client.
            outputQueue = new ArrayBlockingQueue<>(10);

            login();

        } catch (Exception e) {
            // Ignore. Upon client disconnect.
        } finally {
            closeThread();
        }
    }

    private void login() {
        try {
            // Read login request
            String loginRequest = read();
            // Verify login data
            // If valid: Class ClientHandlerWill create a reference to the thread and maps it to a Client.
            ClientHandler.getInstance().login(threadID, getClientIP(), outputQueue, loginRequest);
            // Wait for login result
            String loginResult = outputQueue.take();
            // Forward the login result.
            write(loginResult);
            // Evaluate the login result, according to HoSo protocol.
            boolean successFulLogin = loginResult.startsWith("102") || loginResult.startsWith("104") ||
                    loginResult.startsWith("121");

            if(successFulLogin) {
                // Initiate output loop
                outputThread.start();
                // Initiate input loop.
                inputFromClient();
            }
        } catch (Exception e) {
            // Ignore
        } finally {
            closeThread();
        }
    }

    private void inputFromClient() throws Exception {
        String messageFromClient;
        while (!terminateThread) {
            // Read msg from client
            messageFromClient = read();
            // Log
            Server.getInstance().debugLog("Request from client", threadID, getClientIP(), messageFromClient);
            //TODO: Ahmed & Yehya: Since we spoke I added the below "Check for suspicious input" because it kept reading in "null" from clients that disconnected and the logs from that was eating disk space really fast. So I had to stop that by shutting down the thread if it does not read input according to HoSo protocol-
            try {
                // Check for suspicious input
                Integer.parseInt(messageFromClient.substring(0, 3)); // Should be a 3 digit integer. if not: exception -> close thread
                // Create request
                ClientRequest clientRequest = new ClientRequest(threadID, messageFromClient);
                //Add request to Server request queue for processing
                Server.getInstance().clientRequests.put(clientRequest);
            } catch (Exception e) {
                Server.getInstance().debugLog("Suspicious client input", threadID, getClientIP());
                closeThread();
            }
        }
    }

    private void outputToClient() throws Exception {
        while(!terminateThread) {
            // Block until msg is added by ClientManager on main thread
            String outputMsg = outputQueue.take();
            // Determine action to take
            if(outputMsg.equals("exit")) {
                closeThread();
            } else {
                // Log
                Server.getInstance().debugLog("Msg to client", threadID, getClientIP(), outputMsg);
                // Write to client
                write(outputMsg);
            }
        }
    }

    //TODO: Implement encryption for client communication: custom schemes or JSSE.
    private void write(String message) throws IOException {
        output.println(message);
    }

    private String read() throws IOException {
        return input.readLine();
    }

    private void closeThread() {
        synchronized (lock_closeRes) {
            if(!terminateThread) {
                terminateThread = true;
                try {
                    if (clientConnection != null) {
                        clientConnection.close();
                    }
                    // Ensure output thread closes
                    outputQueue.put("exit");
                    Server.getInstance().debugLog("Shutting down ClientThread", threadID, getClientIP());
                    // Remove from list of connected clients
                    ClientHandler.getInstance().removeConnectedClient(threadID);
                } catch (Exception e) {
                    System.out.println("Error closing client resources for " + getClientIP());
                }
            }
        }
    }

    // Used in log purposes
    private String getClientIP() {
        return clientConnection.getInetAddress().toString().substring(1); // IP-format "/X.X.X.X" to "X.X.X.X"
    }

}
