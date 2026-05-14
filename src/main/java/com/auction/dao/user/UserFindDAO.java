package com.auction.dao.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.auction.database.DatabaseConnection;
import com.auction.model.Entity.User.Bidder;
import com.auction.model.Entity.User.User;

public class UserFindDAO {

    // Tìm user theo username — dùng cho login
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return new Bidder(
                    rs.getString("id"),
                    rs.getString("username"),
                    rs.getString("email"),     // ← thêm
                    rs.getString("password"),
                    rs.getDouble("balance")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Kiểm tra login
    public boolean checkLogin(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();

            return rs.next();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}