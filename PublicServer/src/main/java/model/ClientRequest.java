package model;

public class ClientRequest {
    /**
     *  Maps an incoming request to a particular client session, for
     *  - back tracing, to send individual feedback to a connected client (e.g. passing exception messages)
     *  - enable targeted output even to users logged in on multiple devices simultaneously.
     *
     *  Requests are strings according to HoSo protocol
     */

    public int sessionID;
    public String request; // According to HomeSome protocol

    public ClientRequest(int sessionID, String request) {
        this.sessionID = sessionID;
        this.request = request;
    }

}
