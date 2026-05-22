package com.auction.server.service;

import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.database.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminService — nghiep vu danh rieng cho ADMIN.
 *
 * FIX so voi ban goc:
 *   1. getAllUsers(): tra du thong tin (rating, admin_level)
 *   2. getAllProducts(): them seller_name, description, bid_increment
 *   3. deleteUser(): cascade delete (bids -> auto_bids -> auctions -> items -> users)
 *      Ban goc chi DELETE FROM users — loi foreign key constraint
 */
public class AdminService {

    private final AuctionDAO auctionDAO = new AuctionDAO();

    // ─────────────────────────────────────────────────────────────────────
    // GET ALL PRODUCTS
    // ─────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getAllProducts() {
        String sql = "SELECT i.id, i.name, i.type, i.current_price, i.starting_price, "
                   + "i.seller_id, u.username AS seller_name, i.status, i.end_time, "
                   + "i.description, i.bid_increment "
                   + "FROM items i LEFT JOIN users u ON i.seller_id = u.id "
                   + "ORDER BY i.end_time DESC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",             rs.getString("id"));
                row.put("name",           rs.getString("name"));
                row.put("type",           rs.getString("type"));
                row.put("current_price",  rs.getDouble("current_price"));
                row.put("starting_price", rs.getDouble("starting_price"));
                row.put("seller_id",      rs.getString("seller_id"));
                row.put("seller_name",    rs.getString("seller_name"));
                row.put("status",         rs.getString("status"));
                row.put("end_time",       rs.getString("end_time"));
                row.put("description",    rs.getString("description"));
                row.put("bid_increment",  rs.getDouble("bid_increment"));
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET ALL USERS (FIX: them rating, admin_level)
    // ─────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getAllUsers() {
        String sql = "SELECT id, username, email, role, balance, "
                   + "IFNULL(rating, 0.0) AS rating, "
                   + "IFNULL(admin_level, 0) AS admin_level "
                   + "FROM users ORDER BY role, username";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("id",          rs.getString("id"));
                row.put("username",    rs.getString("username"));
                row.put("email",       rs.getString("email"));
                row.put("role",        rs.getString("role"));
                row.put("balance",     rs.getDouble("balance"));
                row.put("rating",      rs.getDouble("rating"));
                row.put("admin_level", rs.getInt("admin_level"));
                rows.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rows;
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET ALL AUCTIONS
    // ─────────────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getAllAuctions() {
        return auctionDAO.findAll();
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE USER
    // FIX: ban goc chi DELETE users truc tiep -> loi foreign key
    //      Ban fix: cascade delete theo thu tu dung
    // ─────────────────────────────────────────────────────────────────────
    public boolean deleteUser(String userId) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                executeDel(conn, "DELETE FROM bids        WHERE bidder_id = ?", userId);
                executeDel(conn, "DELETE FROM auto_bids   WHERE bidder_id = ?", userId);
                executeDel(conn, "DELETE FROM transactions WHERE winner_id = ?", userId);
                executeDel(conn, "DELETE FROM auctions    WHERE seller_id = ?", userId);
                executeDel(conn, "DELETE FROM items       WHERE seller_id = ?", userId);

                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM users WHERE id = ?")) {
                    ps.setString(1, userId);
                    int rows = ps.executeUpdate();
                    conn.commit();
                    return rows > 0;
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void executeDel(Connection conn, String sql, String param) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        }
    }
}
