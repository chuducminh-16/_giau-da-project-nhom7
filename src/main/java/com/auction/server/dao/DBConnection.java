package com.auction.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Singleton — trả về 1 Connection duy nhất đến SQLite.
 * Chỉ tầng DAO được phép dùng class này.
 */
public class DBConnection {

    private static DBConnection instance;
    private Connection connection;

    // Đường dẫn file SQLite — đặt ở thư mục gốc project
    private static final String DB_URL =
            "jdbc:sqlite:database.db";

    private DBConnection() {
        try {
            // Load driver SQLite
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(DB_URL);
            // Bật foreign key support (SQLite mặc định tắt)
            connection.createStatement()
                    .execute("PRAGMA foreign_keys = ON");
            System.out.println("[DB] Kết nối SQLite thành công.");
        } catch (Exception e) {
            System.err.println("[DB] Lỗi kết nối: " + e.getMessage());
        }
    }

    public static DBConnection getInstance() {
        if (instance == null) instance = new DBConnection();
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }
}