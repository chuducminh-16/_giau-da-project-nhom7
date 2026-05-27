package com.auction.server.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Mỗi lần gọi getConnection() → tạo 1 connection mới.
 * Dùng với try-with-resources để tự đóng sau khi dùng xong.
 * An toàn với multi-thread (mỗi ClientHandler dùng connection riêng).
 */
public class DatabaseConnection {

    private static final String URL      = "jdbc:mysql://kodama.proxy.rlwy.net:38716/railway"
                                         + "?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh"
                                         + "&allowPublicKeyRetrieval=true";
    private static final String USER     = "root";
    private static final String PASSWORD = "UpNyMmOQheSnjWyEeJDqRcMuYriyDmMC";

    static {
        try {
            // 1. Khởi tạo driver hệ thống
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // ══════════════════════════════════════════════════════════════════
            // ➕ TỰ ĐỘNG CẬP NHẬT CẤU TRÚC BẢNG TRÊN CLOUD RAILWAY TẠI ĐÂY
            // ══════════════════════════════════════════════════════════════════
            System.out.println("[Cloud Sync] Dang kiem tra va dong bo 3 cot Profile len Railway...");
            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {
                 
                // Thêm cột full_name
                try { 
                    stmt.execute("ALTER TABLE users ADD COLUMN full_name VARCHAR(255) DEFAULT ''"); 
                    System.out.println("[Cloud Sync] -> Da kiem tra/them cot full_name");
                } catch (Exception e) { /* Cột đã tồn tại, bỏ qua */ }
                
                // Thêm cột phone
                try { 
                    stmt.execute("ALTER TABLE users ADD COLUMN phone VARCHAR(20) DEFAULT ''"); 
                    System.out.println("[Cloud Sync] -> Da kiem tra/them cot phone");
                } catch (Exception e) { /* Cột đã tồn tại, bỏ qua */ }
                
                // Thêm cột address
                try { 
                    stmt.execute("ALTER TABLE users ADD COLUMN address VARCHAR(255) DEFAULT ''"); 
                    System.out.println("[Cloud Sync] -> Da kiem tra/them cot address");
                } catch (Exception e) { /* Cột đã tồn tại, bỏ qua */ }
                
                System.out.println("[Cloud Sync] == DONG BO HO SO LEN CLOUD RAILWAY THANH CONG! ==");
            } catch (SQLException e) {
                System.err.println("[Cloud Sync] Khong the ket noi de cap nhat bảng: " + e.getMessage());
            }
            // ══════════════════════════════════════════════════════════════════
            
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