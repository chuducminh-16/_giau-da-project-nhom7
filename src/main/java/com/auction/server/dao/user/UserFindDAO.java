package com.auction.server.dao.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.User.Admin;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

public class UserFindDAO {

    /**
     * Tìm user theo username — dùng cho login.
     * Map đúng role: BIDDER → Bidder, SELLER → Seller, ADMIN → Admin.
     */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapRow(rs);
            }

        } catch (Exception e) {
            System.err.println("[UserFindDAO] findByUsername ERROR: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Kiểm tra login (username + password).
     */
    public boolean checkLogin(String username, String password) {
        String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();

        } catch (Exception e) {
            System.err.println("[UserFindDAO] checkLogin ERROR: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Map ResultSet → đúng subclass User theo cột role trong DB.
     */
    private User mapRow(ResultSet rs) throws Exception {
        String id       = rs.getString("id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password");
        String role     = rs.getString("role");
        double balance  = rs.getDouble("balance");
        double rating   = rs.getDouble("rating");
        int adminLevel  = rs.getInt("admin_level");

        return switch (role != null ? role.toUpperCase() : "BIDDER") {
            case "SELLER" -> new Seller(id, username, email, password, rating);
            case "ADMIN"  -> new Admin(id, username, email, password, adminLevel);
            default       -> new Bidder(id, username, email, password, balance);
        };
    }
}