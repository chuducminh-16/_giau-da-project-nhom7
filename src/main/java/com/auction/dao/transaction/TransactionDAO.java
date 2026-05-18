package com.auction.dao.transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.auction.database.DatabaseConnection;

public class TransactionDAO {

    // Lưu giao dịch khi đấu giá kết thúc
    public boolean saveTransaction(String itemId, String winnerId, double finalPrice) {
        String sql = "INSERT INTO transactions (item_id, winner_id, final_price) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            pstmt.setString(2, winnerId);
            pstmt.setDouble(3, finalPrice);

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Xem lịch sử giao dịch của 1 user
    public List<Map<String, Object>> getByUser(String userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT t.id, i.name, t.final_price, t.transaction_time " +
                     "FROM transactions t JOIN items i ON t.item_id = i.id " +
                     "WHERE t.winner_id = ? ORDER BY t.transaction_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("transactionId", rs.getInt("id"));
                row.put("itemName",      rs.getString("name"));
                row.put("finalPrice",    rs.getDouble("final_price"));
                row.put("time",          rs.getString("transaction_time"));
                list.add(row);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // Xem tất cả giao dịch (dành cho Admin)
    public List<Map<String, Object>> getAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT t.id, i.name, u.username, t.final_price, t.transaction_time " +
                     "FROM transactions t " +
                     "JOIN items i ON t.item_id = i.id " +
                     "JOIN users u ON t.winner_id = u.id " +
                     "ORDER BY t.transaction_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("transactionId", rs.getInt("id"));
                row.put("itemName",      rs.getString("name"));
                row.put("winner",        rs.getString("username"));
                row.put("finalPrice",    rs.getDouble("final_price"));
                row.put("time",          rs.getString("transaction_time"));
                list.add(row);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
