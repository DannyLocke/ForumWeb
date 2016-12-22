package com.ironyard;

import org.h2.tools.Server;
import spark.ModelAndView;
import spark.Session;
import spark.Spark;
import spark.template.mustache.MustacheTemplateEngine;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Main {

//    static HashMap<String, User> users = new HashMap<>();
//    static ArrayList<Message> messages = new ArrayList<>();

    public static void main(String[] args) throws SQLException{
        Server.createWebServer().start();
        Connection conn = DriverManager.getConnection("jdbc:h2:./main");
        createTables(conn);

        addTestUsers(conn);
        addTestMessages(conn);

        Spark.init();

        //create the "/" route to send down the HTML
        Spark.get(
                "/",
                ((request, response) -> {
                    String replyId = request.queryParams("replyId");
                    int replyIdNum = - 1;
                    if (replyId != null){
                        replyIdNum = Integer.valueOf(replyId);
                    }

                    HashMap m = new HashMap();
                    ArrayList<Message> threads = selectReplies(conn, replyIdNum);

                    Session session = request.session();
                    String userName = session.attribute("userName");

                    m.put("messages", threads);
                    m.put("userName", userName);
                    m.put("replyId", replyIdNum);
                    return  new ModelAndView(m, "home.html");
                }),
                new MustacheTemplateEngine()
        );//end Spark.get

        //route for login
        Spark.post(
                "/login",
                ((request, response) -> {
                    String userName = request.queryParams("loginName");
                    if (userName == null){
                        throw new Exception("Login name not found.");
                    }

                    User user = selectUser(conn, userName);
                    if (user == null){
                        insertUser(conn, userName, "");
                    }
                    Session session = request.session();
                    session.attribute("userName", userName);

                    response.redirect("/");
                    return "";
                })
        );//end Spark /login


        Spark.post(
                "/create-message",
                ((request, response) -> {
                    Session session = request.session();
                    String userName = session.attribute("userName");
                    if (userName == null) {
                        throw new Exception("Not logged in.");
                    }

                    String text = request.queryParams("messageText");
                    String replyId = request.queryParams("replyId");
                    if (text == null || replyId == null) {
                        throw new Exception("Didn't get necessary query parameters.");
                    }

                    int replyIdNum = Integer.valueOf(replyId);

                    //get user from database
                    User user = selectUser(conn, userName);
                    //insert message into database
                    insertMessage(conn, user.id, replyIdNum, text);

                    response.redirect(request.headers("Referer"));
                    return "";
                })
        );

        //route for logout
        Spark.post(
                "/logout",
                ((request, response) -> {
                    Session session = request.session();
                    session.invalidate();
                    response.redirect("/");
                    return"";
                })
        );//end Spark /logout

    }//end main()

    public static void addTestUsers(Connection conn) throws SQLException {
        insertUser(conn, "Alice", "");
        insertUser(conn, "Bob", "");
        insertUser(conn, "Charlie", "");
    }

    static void addTestMessages(Connection conn) throws SQLException {
        insertMessage(conn, 1, -1, "Hello world!");
        insertMessage(conn, 2, -1, "This is another thread.");
        insertMessage(conn, 3, 1, "Cool thread, Alice!");
        insertMessage(conn, 1, 2, "Thanks");
    }



    public static void createTables(Connection conn) throws SQLException {
        Statement stmt = conn.createStatement();
        stmt.execute("CREATE TABLE IF NOT EXISTS users (id IDENTITY, name VARCHAR, password VARCHAR)");
        stmt.execute("CREATE TABLE IF NOT EXISTS messages (id IDENTITY, userID INT, reply_Id INT, text VARCHAR)");
    }

    public static void insertUser(Connection conn, String name, String password) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO users VALUES (NULL, ?, ?)");
        stmt.setString(1, name);
        stmt.setString(2, password);
        stmt.execute();
    }

    public static User selectUser(Connection conn, String name) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE name = ?");
        stmt.setString(1, name);
        ResultSet results = stmt.executeQuery();
        if (results.next()) {
            int id = results.getInt("id");
            String password = results.getString("password");
            return new User(id, name, password);
        }
        return null;
    }

    public static void insertMessage(Connection conn, int userId, int replyId, String text) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("INSERT INTO messages VALUES (NULL, ?, ?, ?)");
        stmt.setInt(1, userId);
        stmt.setInt(2, replyId);
        stmt.setString(3, text);
        stmt.execute();
    }

    public static Message selectMessage(Connection conn, int id) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM messages " +
                "INNER JOIN users ON messages.userID = users.id WHERE messages.id = ?");
        stmt.setInt(1, id);
        ResultSet results = stmt.executeQuery();
        if (results.next()) {
            int replyId = results.getInt("messages.reply_Id");  //qualified as we're using 2 tables
            String name = results.getString("users.name");
            String text = results.getString("messages.text");
            return new Message(id, replyId, name, text);
        }
        return null;
    }

    public static ArrayList<Message> selectReplies (Connection conn, int replyId) throws SQLException {
        ArrayList<Message> messages = new ArrayList<>();

        PreparedStatement stmt = conn.prepareStatement("SELECT * FROM messages " +
                "INNER JOIN users ON messages.userID = users.id WHERE messages.reply_Id = ?");
        stmt.setInt(1, replyId);
        ResultSet results = stmt.executeQuery();

        while(results.next()){
            int id = results.getInt("messages.id");  //qualified as we're using 2 tables
            String name = results.getString("users.name");
            String text = results.getString("messages.text");
            Message message = new Message (id, replyId, name, text);
            messages.add(message);
        }
        return messages;
    }


}//end class Main
