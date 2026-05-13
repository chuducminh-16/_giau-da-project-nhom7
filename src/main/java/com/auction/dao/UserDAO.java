package com.auction.dao;

import com.auction.database.DatabaseConnection;
import com.auction.model.Bidder;
import com.auction.model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    // ✅ Lưu user mới vào DB
    public boolean saveUser(User user) {
        String sql = "INSERT INTO users (id, username, password, role) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getPassword());
            pstmt.setString(4, user.getRole());

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ✅ Tìm user theo username (dùng cho login)
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
                    rs.getString("password"),
                    rs.getDouble("balance")    // ← thêm dòng này
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ✅ Lấy toàn bộ danh sách user
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                users.add(new Bidder(
                    rs.getString("id"),
                    rs.getString("username"),
                    rs.getString("password"),
                    rs.getDouble("balance")    // ← thêm dòng này
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }
}