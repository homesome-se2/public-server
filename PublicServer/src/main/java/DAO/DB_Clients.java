package DAO;

import service.Server;
import org.json.simple.JSONObject;


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


    public DB_Clients() {
        setDbSpecs();
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

        //System.out.println(ip+port+database+"<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");

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

        connect();
        JSONObject items = new JSONObject();
        int results = 0;
        try {
                  // Get hubID and admin-state
                  preparedStatement = connection.prepareStatement("SELECT client_hub_hubId, isAdmin FROM hoso.client_user WHERE nameId = ? AND pass = ?;");
                  preparedStatement.setString(1, nameID);
                  preparedStatement.setString(2, password);
                  resultSet = preparedStatement.executeQuery();

                  //Note: If Query gives no result, the while(next) below won't launch.
                  while (resultSet.next()) {
                      results++;
                      //We already have user name from the method parameters (so we don't need to acquire it from DB_Users).
                      int hubID = resultSet.getInt("client_hub_hubId"); // As String, while it is an integer in MySQL
                      boolean admin = resultSet.getBoolean("isAdmin");

                      items.put("hubId", hubID);
                      items.put("isAdmin", admin);
                  }

                  if (results != 1) { //If there was to few matches, or for some reason, multiple matches.
                      // This will be sent to the user before closing the connection.
                      throw new Exception("Login failed. Connection is good");
                  }
                  // Update sessionKey to DB_Users
                  preparedStatement = connection.prepareStatement("UPDATE hoso.client_user SET sessionKey = ? WHERE nameId = ?;");
                  preparedStatement.setString(1, newSessionKey);
                  preparedStatement.setString(2, nameID);
                  results = preparedStatement.executeUpdate();
                  if (results != 1) {
                      throw new Exception("Server unable to update session key. Code 1");
                  }
              } catch (SQLException e) {
                  throw new Exception("Error on SQL query. Code 1");
              } catch (NullPointerException e) {
                  throw new Exception("NullPointer Exception");
              } finally {
                  closeConnection();
        }
              return items;
    }

    public JSONObject automaticUserLogin(String nameID, String sessionKey) throws Exception {
        //TODO: Implement automaticUserLogin()
        // IF VALID:
        // Return hubID (int) & admin (boolean) from DB. Return in JSON object.
        // IF INVALID:
        // Throws exception with custom exception msg; eg. "Login failed. Connection is good"
        connect();
        JSONObject info = new JSONObject();
        int results = 0;

        try {
            // Get hubID and admin-state
            preparedStatement = connection.prepareStatement("SELECT client_hub_hubId, isAdmin FROM hoso.client_user WHERE nameId = ? AND sessionKey = ?;");
            preparedStatement.setString(1, nameID);
            preparedStatement.setString(2, sessionKey);
            resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                results++;
                //We already have user name from the method parameters (so we don't need to acquire it from DB_Users).
                int hubID = resultSet.getInt("client_hub_hubId"); // As String, while it is an integer in MySQL
                boolean admin = resultSet.getBoolean("isAdmin");

                info.put("hubId", hubID);
                info.put("isAdmin", admin);
            }

            if (results != 1) {
                throw new Exception("AutoLogin failed. Connection is good");
            }
        } catch (SQLException e) {
        throw new Exception("Error on SQL query. Code 1");
    } catch (NullPointerException e) {
        throw new Exception("NullPointer Exception");
    } finally {
        closeConnection();
    }
        return info;
    }

    public boolean hubLogin(int hubID, String password) {
        //TODO: Implement hubLogin()
        // Does not return any data from DB. Valid login: return true. Invalid login: return false.
        // NOTE: This method should not through any Exceptions back the stack trace.
        connect();
        int results = 0;
        boolean check = false;
        String pass;
        int theHubID;

        try {
            // check valid login
            preparedStatement = connection.prepareStatement("SELECT hubId, pass FROM hoso.client_hub WHERE hubId = ? AND pass = ?;");
            preparedStatement.setInt(1, hubID);
            preparedStatement.setString(2, password);
            resultSet = preparedStatement.executeQuery();

            if (resultSet.next()){
                results++;
                check = true;
                //System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< is found");
            }
            if (results != 1){
                check = false;
            }

        }catch (SQLException e){
        }

        return check;
    }

    public void manualUserLogout(String nameId, String sessionKey)throws Exception{
        connect();
        int result = 0;

        try {
            preparedStatement = connection.prepareStatement("UPDATE hoso.client_user SET sessionKey = ? WHERE nameId = ?;");
            preparedStatement.setString(1, sessionKey);
            preparedStatement.setString(2, nameId);
            result = preparedStatement.executeUpdate();
            if (result != 1) {
                throw new Exception("Server unable to update session key. Code 1");
            }
        } catch (SQLException e) {
            throw new Exception("Error on SQL query. Code 1");
        } catch (NullPointerException e) {
            throw new Exception("NullPointer Exception");
        } finally {
            closeConnection();
        }
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
