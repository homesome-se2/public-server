package model;

public class ClientRequest {
    /**
     *  Maps an incoming request to a particular client thread, for
     *  - back tracing, to send individual feedback to a connected client (e.g. passing exception messages)
     *  - enable targeted output even to users logged in on multiple devices simultaneously.
     *
     *  Requests are strings according to HoSo protocol
     */

    public int threadID;
    public String request; // According to HomeSome protocol

    public ClientRequest(int threadID, String request) {
        this.threadID = threadID;
        this.request = request;
    }

}
