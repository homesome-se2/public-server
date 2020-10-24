package main.java.temp_mock;


import main.java.model.ClientRequest;
import main.java.service.ClientHandler;
import main.java.service.Server;

public class Mock_Interaction {

    /**
     * The below mock interaction imitates data coming from a hub
     * for forwarding to associated clients.
     *
     * @see #hubReportsAllGadgets
     * Simulating a hub sending all its gadgets to the Public Server
     * for forwarding to a newly logged in user.
     *
     * @see #hubReportsGadgetState()
     * Simulating a hub reporting changes in state to a gadget.
     * Sends out to all connected users in intervals of 12 sec.
     *
     */

    private Thread reportStateThread;
    public boolean isGadgetOneOn;


    public Mock_Interaction() {
        isGadgetOneOn = false;
        reportStateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                   // hubReportsGadgetState();
                    //hubReportsAllGadgets();
                } catch (Exception e) {
                    close();
                }
            }
        });
    }

    public void launch() {
        reportStateThread.start();
    }

    public  void close() {
        reportStateThread.interrupt();
    }

    // hub answer with 303 on the ps request 302
    public void hubReportsAllGadgets(int threadIdIssuingClient) {
        // Reports 4 gadgets:
        // - My lamp (SWITCH)
        // - Front door (BINARY_SENSOR)
        // - Kitchen temp (SENSOR)
        // - Window lamp (SWITCH)

        String protocolString =
                "303::" + threadIdIssuingClient + "::4::" +
                        "1::My Lamp::SWITCH::light::" + (isGadgetOneOn? "1":"0") + "::30::" +
                        "2::Front door::BINARY_SENSOR::door::0.0::2::" +
                        "3::Kitchen temp::SENSOR::temp::21.0::120::" +
                        "4::Window lamp::SWITCH::light::1::60";

        // Add as a request to the server
        ClientRequest hubRequest = new ClientRequest(threadIdIssuingClient, protocolString);
        try {
            Server.getInstance().clientRequests.put(hubRequest);
        } catch (InterruptedException e) {
            // Ignore
        }
    }


    public void hubReportsGadgetState() throws Exception {
        // Toggles the gadget:
        // My lamp (SWITCH) Gadget id: 1
        int gadgetID = 1;
        while(!Server.getInstance().terminateServer) {
            Thread.sleep(12000); // 12 sec
            int availableSessionID = ClientHandler.getInstance().getSessionID();
            if(availableSessionID != -1) {
                isGadgetOneOn = !isGadgetOneOn; // toggle
                String gadgetUpdate = String.format("%s::%s::%s", "316", gadgetID, isGadgetOneOn ? 1 : 0);
                ClientHandler.getInstance().outputToClients(availableSessionID, false, false, false, gadgetUpdate);
            }

        }
    }

    public void requestGadgetGroups (int sessionIdIssuingClient) {
        // Reports 3 groups:
        // - All lamps   [1, 4]
        // - All gadgets [1, 2, 3, 4]
        // - Kitchen     [3, 4]
        String protocolString =
                "372::" + sessionIdIssuingClient +
                        "::All Lamps:1:4" +
                        "::All Gadgets:1:2:3:4" +
                        "::Kitchen:3:4";

        // Add as a request to the server
        ClientRequest hubRequest = new ClientRequest(1, protocolString);
        try {
            Server.getInstance().clientRequests.put(hubRequest);
        } catch (InterruptedException e) {
            // Ignore
        }
    }
}
