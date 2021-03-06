package web_resource;

import service.ClientHandler;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

@WebSocket // Annotate that this is a WebSocket class
public class WebSocketServer {

    // New client connected to WebSocket server
    @OnWebSocketConnect
    public void onConnect(Session session) throws Exception {
        //session.setIdleTimeout(60*1000); // Server closes session (connection) if idle.
        //System.out.println("WE R HERE on connect>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

        ClientHandler.getInstance().addClient(session);
    }

    // Client disconnected from WebSocket server
    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) throws Exception{
        ClientHandler.getInstance().removeClient(session);
    }

    // Message from client
    @OnWebSocketMessage
    public void onMessage(Session session, String message) throws Exception {
        //System.out.println("WE R HERE  ON MESSAGE >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        ClientHandler.getInstance().addClientRequest(session, message);
    }
}
