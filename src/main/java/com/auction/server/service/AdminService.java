package com.auction.server.service;

import com.auction.server.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Xử lý các nghiệp vụ dành riêng cho ADMIN.
 */
public class AdminService {

    // ── Lấy tất cả sản phẩm (kể cả FINISHED) ─────────
    public List<Map<String, Object>> getAllProducts() {
        String sql = "SELECT * FROM items ORDER BY end_time DESC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",            rs.getString("id"));
                row.put("name",          rs.getString("name"));
                row.put("type",          rs.getString("type"));
                row.put("current_price", rs.getDouble("current_price"));
                row.put("seller_id",     rs.getString("seller_id"));
                row.put("status",        rs.getString("status"));
                row.put("end_time",      rs.getString("end_time"));
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

    // ── Lấy tất cả users ──────────────────────────────
    public List<Map<String, Object>> getAllUsers() {
        String sql = "SELECT id, username, email, role, balance " +
                     "FROM users ORDER BY role, username";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",       rs.getString("id"));
                row.put("username", rs.getString("username"));
                row.put("email",    rs.getString("email"));
                row.put("role",     rs.getString("role"));
                row.put("balance",  rs.getDouble("balance"));
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

    // ── Lấy tất cả phiên đấu giá ──────────────────────
    public List<Map<String, Object>> getAllAuctions() {
        String sql = "SELECT a.id, a.item_id, i.name as item_name, a.seller_id, " +
                     "a.current_price, a.status, a.end_time " +
                     "FROM auctions a LEFT JOIN items i ON a.item_id = i.id " +
                     "ORDER BY a.end_time DESC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",           rs.getLong("id"));
                row.put("itemId",       rs.getString("item_id"));
                row.put("itemName",     rs.getString("item_name"));
                row.put("sellerId",     rs.getString("seller_id"));
                row.put("currentPrice", rs.getDouble("current_price"));
                row.put("status",       rs.getString("status"));
                row.put("endTime",      rs.getString("end_time"));
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

    // ── Xóa user theo ID ──────────────────────────────
    public boolean deleteUser(String userId) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}