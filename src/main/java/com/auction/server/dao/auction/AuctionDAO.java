package com.auction.server.dao.auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.auction.server.database.DatabaseConnection;

public class AuctionDAO {

    // Lưu phiên đấu giá mới vào DB
    public boolean saveAuction(long auctionId, String itemId, String sellerId,
                                double startingPrice, String endTime) {
        String sql = "INSERT INTO auctions (id, item_id, seller_id, current_price, status, end_time) " +
                     "VALUES (?, ?, ?, ?, 'OPEN', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, auctionId);
            pstmt.setString(2, itemId);
            pstmt.setString(3, sellerId);
            pstmt.setDouble(4, startingPrice);
            pstmt.setString(5, endTime);

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Tìm phiên theo id
    public Map<String, Object> findById(long auctionId) {
        String sql = "SELECT a.*, i.name as item_name, i.current_price " +
                     "FROM auctions a JOIN items i ON a.item_id = i.id " +
                     "WHERE a.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, auctionId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) return mapRow(rs);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Lấy tất cả phiên đang OPEN hoặc RUNNING
    public List<Map<String, Object>> findAllOpen() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name as item_name, i.current_price " +
                     "FROM auctions a JOIN items i ON a.item_id = i.id " +
                     "WHERE a.status IN ('OPEN', 'RUNNING') AND a.end_time > NOW() " +
                     "ORDER BY a.end_time ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // Lấy tất cả phiên (dành cho Admin)
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name as item_name, i.current_price " +
                     "FROM auctions a JOIN items i ON a.item_id = i.id " +
                     "ORDER BY a.end_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // Cập nhật trạng thái phiên: OPEN → RUNNING → FINISHED → PAID / CANCELED
    public boolean updateStatus(long auctionId, String newStatus) {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newStatus);
            pstmt.setLong(2, auctionId);
            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Cập nhật giá hiện tại khi có bid mới
    public boolean updateCurrentPrice(long auctionId, double newPrice) {
        String sql = "UPDATE auctions SET current_price = ?, status = 'RUNNING' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, newPrice);
            pstmt.setLong(2, auctionId);
            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Lấy người thắng — bidder đặt giá cao nhất trong phiên
    public Map<String, Object> findWinner(long auctionId) {
        String sql = "SELECT b.bidder_id, u.username, b.bid_price " +
                     "FROM bids b " +
                     "JOIN auctions a ON b.item_id = a.item_id " +
                     "JOIN users u ON b.bidder_id = u.id " +
                     "WHERE a.id = ? " +
                     "ORDER BY b.bid_price DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, auctionId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                Map<String, Object> winner = new HashMap<>();
                winner.put("bidderId", rs.getString("bidder_id"));
                winner.put("username", rs.getString("username"));
                winner.put("finalPrice", rs.getDouble("bid_price"));
                return winner;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // Lấy phiên theo seller
    public List<Map<String, Object>> findBySeller(String sellerId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name as item_name, i.current_price " +
                     "FROM auctions a JOIN items i ON a.item_id = i.id " +
                     "WHERE a.seller_id = ? ORDER BY a.end_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sellerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) list.add(mapRow(rs));

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // Map ResultSet → Map dùng chung
    private Map<String, Object> mapRow(ResultSet rs) throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("id",           rs.getLong("id"));
        row.put("itemId",       rs.getString("item_id"));
        row.put("itemName",     rs.getString("item_name"));
        row.put("sellerId",     rs.getString("seller_id"));
        row.put("currentPrice", rs.getDouble("current_price"));
        row.put("status",       rs.getString("status"));
        row.put("endTime",      rs.getString("end_time"));
        return row;
    }
}