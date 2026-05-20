package com.auction.server.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

<<<<<<< HEAD
public class DatabaseConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/auction_db";
    private static final String USER = "root";  // thay bằng username của bạn
    private static final String PASSWORD = "";   // thay bằng password của bạn
    
    private static Connection connection = null;
    
    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(URL, USER, PASSWORD);
                System.out.println("Kết nối database thành công!");
            } catch (ClassNotFoundException e) {
                throw new SQLException("Không tìm thấy MySQL Driver", e);
            }
        }
        return connection;
    }
    
    public static void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
=======
/**
 * Mỗi lần gọi getConnection() → tạo 1 connection mới.
 * Dùng với try-with-resources để tự đóng sau khi dùng xong.
 * An toàn với multi-thread (mỗi ClientHandler dùng connection riêng).
 *
 * Cách dùng:
 *   try (Connection conn = DatabaseConnection.getConnection();
 *        PreparedStatement ps = conn.prepareStatement(sql)) {
 *       ...
 *   }  // ← conn tự đóng ở đây
 */
public class DatabaseConnection {

    private static final String URL      = "jdbc:mysql://localhost:3306/auction_db"
                                         + "?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh"
                                         + "&allowPublicKeyRetrieval=true";
    private static final String USER     = "root";
    private static final String PASSWORD = "";   // ← đổi thành password MySQL của bạn

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("Không tìm thấy MySQL Driver: " + e.getMessage());
        }
    }

    private DatabaseConnection() {} // không cho new

    /**
     * Trả về 1 Connection mới mỗi lần gọi.
     * Caller phải đóng connection (dùng try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
>>>>>>> 047e37a682ea24854e4fa3367031b48d42a35874
