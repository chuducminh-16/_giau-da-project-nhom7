package com.auction.dao;

import com.auction.database.DatabaseConnection;
import com.auction.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;

public class UserSaveDAO {

    // Lưu user mới vào DB
    public boolean saveUser(User user) {
        String sql = "INSERT INTO users (id, username, password, role, balance) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getPassword());
            pstmt.setString(4, user.getRole());
            pstmt.setDouble(5, 0.0); // balance mặc định khi đăng ký

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
