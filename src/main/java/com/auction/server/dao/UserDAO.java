package com.auction.server.dao;

import com.auction.model.Entity.User.User;
import com.auction.model.Entity.User.Bidder;
import com.auction.model.Entity.User.Seller;
import com.auction.model.Entity.User.Admin;

import java.sql.*;
import java.util.UUID;

public class UserDAO {

    private final Connection conn =
            DBConnection.getInstance().getConnection();

    // ── Tìm user theo email (dùng cho login) ──────────
    public User findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("[UserDAO] findByEmail: " + e.getMessage());
        }
        return null;
    }

    // ── Tìm user theo username ─────────────────────────
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("[UserDAO] findByUsername: " + e.getMessage());
        }
        return null;
    }

    // ── Kiểm tra email đã tồn tại chưa ────────────────
    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Kiểm tra username đã tồn tại chưa ─────────────
    public boolean existsByUsername(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Thêm user mới ──────────────────────────────────
    public boolean save(String username, String email,
                        String hashedPassword, String fullName,
                        String phone, String address, String role) {
        String sql = """
            INSERT INTO users
                (id, username, email, password,
                 full_name, phone, address, role)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, username);
            ps.setString(3, email);
            ps.setString(4, hashedPassword);
            ps.setString(5, fullName);
            ps.setString(6, phone);
            ps.setString(7, address);
            ps.setString(8, role);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("[UserDAO] save: " + e.getMessage());
            return false;
        }
    }

    // ── Map ResultSet → User object ────────────────────
    private User mapRow(ResultSet rs) throws SQLException {
        String id       = rs.getString("id");
        String username = rs.getString("username");
        String password = rs.getString("password");
        String email    = rs.getString("email");
        String role     = rs.getString("role");

        return switch (role) {
            case "SELLER" -> new Seller(id, username, password, email, 5.0);
            case "ADMIN"  -> new Admin(id, username, password, email, 5);
            default       -> new Bidder(id, username, password, email, 0.0);
        };
    }
}