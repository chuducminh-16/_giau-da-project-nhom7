package com.auction.dao;

import com.auction.database.DatabaseConnection;
import com.auction.model.Bidder;
import com.auction.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserListDAO {

    // Lấy toàn bộ danh sách user
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
                    rs.getDouble("balance")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }

    // Lấy danh sách user theo role (BIDDER, SELLER, ADMIN)
    public List<User> findByRole(String role) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, role);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                users.add(new Bidder(
                    rs.getString("id"),
                    rs.getString("username"),
                    rs.getString("password"),
                    rs.getDouble("balance")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }
}
