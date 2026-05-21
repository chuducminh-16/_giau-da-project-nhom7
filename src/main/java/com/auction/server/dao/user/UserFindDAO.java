package com.auction.server.dao.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.User.Admin;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

/**
 * DAO tìm kiếm user.
 * FIX: trả đúng subclass (Bidder / Seller / Admin) dựa theo cột role trong DB.
 * Trước đây luôn trả Bidder → Seller/Admin bị phân quyền sai.
 */
public class UserFindDAO {

    /**
     * Tìm user theo username — dùng cho login.
     * Trả về đúng subclass tương ứng với role trong DB.
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
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Tìm user theo ID — dùng cho các service cần resolve userId.
     */
    public User findById(String userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapRow(rs);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Kiểm tra login nhanh (chỉ dùng khi không cần User object).
     */
    public boolean checkLogin(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND password = ?";
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

    // ── Map ResultSet → đúng subclass theo role ──────────────────────────

    private User mapRow(ResultSet rs) throws Exception {
        String id       = rs.getString("id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password");
        String role     = rs.getString("role");
        double balance  = rs.getDouble("balance");
        double rating   = rs.getDouble("rating");
        int    adminLvl = rs.getInt("admin_level");

        return switch (role == null ? "BIDDER" : role.toUpperCase()) {
            case "SELLER" -> new Seller(id, username, email, password, rating);
            case "ADMIN"  -> new Admin(id, username, email, password, adminLvl);
            default       -> new Bidder(id, username, email, password, balance);
        };
    }
}