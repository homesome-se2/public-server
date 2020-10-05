package DAO;

import org.json.simple.JSONObject;
import service.Server;

import java.sql.*;

public class DB_Clients {

    // DB authentication
    private String ip;
    private String port;
    private String database;
    private String account;
    private String password;

    //DB operations
    private Connection connection;
    private PreparedStatement preparedStatement;
    private ResultSet resultSet;

    // Lock object
    private final Object lock_db;

    // Singleton
    private static DB_Clients instance = null;

    public static DB_Clients getInstance() {
        if (instance == null) {
            instance = new DB_Clients();
            instance.setDbSpecs();
        }
        return instance;
    }

    private DB_Clients() {
        lock_db = new Object();
    }

    private void setDbSpecs() {
        // Initiate DB specs with Settings data read from config.json
        try {
            String[] dbSpecs = Server.getInstance().settings.getDbSpecs();
            ip = dbSpecs[0];
            port = dbSpecs[1];
            database = dbSpecs[2];
            account = dbSpecs[3];
            password = dbSpecs[4];
        } catch (Exception e) {
            System.out.println("Unable to read DB_specs from Settings.");
        }
    }

    private void connect() {
        connection = null;

        String url = "jdbc:mysql://" + ip + ":" + port + "/" + database + "?useSSL=false&user=" + account + "&password=" + password + "&serverTimezone=UTC";
        try {
            connection = DriverManager.getConnection(url);
            preparedStatement = null;
        } catch (SQLException ex) {
            System.out.println("DB_Clients connection error");
            System.out.println(ex.getMessage());
            closeConnection();
        }
    }

    private void closeConnection() {
        if (resultSet != null) {
            try {
                resultSet.close();
            } catch (SQLException e) {
                System.out.println("Error on closing DB_Clients ResultSet");
            }
        }

        if (preparedStatement != null) {
            try {
                preparedStatement.close();
                preparedStatement = null;
            } catch (SQLException e) {
                System.out.println("Error on closing DB_Clients PreparedStatement");
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.out.println("Error on closing DB_Clients connection");
            }
        }
    }

    public JSONObject manualUserLogin(String nameID, String password, String newSessionKey) throws Exception {

        //TODO: Implement manualUserLogin()
        // 1. Login verification: Something like: SELECT hubID & admin FROM Client_Users WHERE nameID == nameID & password == password

        // IF VALID
        // Update client's sessionKey in DB with newSessionKey
        // Return hubID (int) & admin(boolean) from DB. Return as JSON object.
        // IF INVALID:
        // Throws exception with custom exception msg; eg. "Login failed. Connection is good" (Will be sent to the client)

        // See example implementation with prepared statements at bottom of this page.

        // TEMPORARY MOCK:
        JSONObject items = new JSONObject();
        items.put("hubID", 101);
        items.put("admin", true);
        return items;
    }

    public JSONObject automaticUserLogin(String nameID, String sessionKey) throws Exception {
        //TODO: Implement automaticUserLogin()
        // IF VALID:
        // Return hubID (int) & admin (boolean) from DB. Return in JSON object.
        // IF INVALID:
        // Throws exception with custom exception msg; eg. "Login failed. Connection is good"
        return new JSONObject();
    }

    public boolean hubLogin(int hubID, String password) {
        //TODO: Implement hubLogin()
        // Does not return any data from DB. Valid login: return true. Invalid login: return false.
        // NOTE: This method should not through any Exceptions back the stack trace.
        return false;
    }

    /**
     * ========================== EXAMPLE METHOD FOR manualUserLogin() ===============================================================
     *
     * public JSONObject manualUserLogin(String nameID, String password, String newSessionKey) throws Exception {
     *         // Catch or declare. Here: declare so we can pass exception messages back to client application, eg: "Login failed. Connection is good"
     *
     *         connect();
     *         JSONObject items = new JSONObject();
     *         int results = 0;
     *         try {
     *             // Get hubID and admin-state
     *             preparedStatement = connection.prepareStatement("SELECT hubID, admin FROM Client_Users WHERE nameID = ? AND password = ?;");
     *             preparedStatement.setString(1, nameID);
     *             preparedStatement.setString(2, password);
     *             resultSet = preparedStatement.executeQuery();
     *
     *             //Note: If Query gives no result, the while(next) below won't launch.
     *             while (resultSet.next()) {
     *                 results++;
     *                 //We already have user name from the method parameters (so we don't need to acquire it from DB_Users).
     *                 int hubID = resultSet.getInt("hubID"); // As String, while it is an integer in MySQL
     *                 boolean admin = resultSet.getBoolean("admin");
     *
     *                 items.put("hubID", hubID);
     *                 items.put("admin", admin);
     *             }
     *
     *             if (results != 1) { //If there was to few matches, or for some reason, multiple matches.
     *                 // This will be sent to the user before closing the connection.
     *                 throw new Exception("Login failed. Connection is good");
     *             }
     *             // Update sessionKey to DB_Users
     *             preparedStatement = connection.prepareStatement("UPDATE Client_Users SET sessionKey = ? WHERE nameID = ?;");
     *             preparedStatement.setString(1, newSessionKey);
     *             preparedStatement.setString(2, nameID);
     *             results = preparedStatement.executeUpdate();
     *             if (results != 1) {
     *                 throw new Exception("Server unable to update session key. Code 1");
     *             }
     *         } catch (SQLException e) {
     *             throw new Exception("Error on SQL query. Code 1");
     *         } catch (NullPointerException e) {
     *             throw new Exception("NullPointer Exception");
     *         } finally {
     *             closeConnection();
     *         }
     *         return items;
     *     }
     *
     */

}
