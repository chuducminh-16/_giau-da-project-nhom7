package com.auction.dao.bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.auction.database.DatabaseConnection;

public class BidDAO {

    // Đặt giá mới
    public boolean placeBid(String itemId, String bidderId, double bidPrice) {
        String sql = "INSERT INTO bids (item_id, bidder_id, bid_price) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            pstmt.setString(2, bidderId);
            pstmt.setDouble(3, bidPrice);

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // Xem lịch sử đặt giá của 1 sản phẩm
    public List<Map<String, Object>> getBidHistory(String itemId) {
        List<Map<String, Object>> history = new ArrayList<>();
        String sql = "SELECT b.bidder_id, u.username, b.bid_price, b.bid_time " +
                     "FROM bids b JOIN users u ON b.bidder_id = u.id " +
                     "WHERE b.item_id = ? ORDER BY b.bid_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("bidderId", rs.getString("bidder_id"));
                row.put("username", rs.getString("username"));
                row.put("bidPrice", rs.getDouble("bid_price"));
                row.put("bidTime",  rs.getString("bid_time"));
                history.add(row);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return history;
    }

    // Lấy giá cao nhất hiện tại của 1 sản phẩm
    public double getHighestBid(String itemId) {
        String sql = "SELECT MAX(bid_price) FROM bids WHERE item_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) return rs.getDouble(1);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    // Lấy người đang thắng (đặt giá cao nhất)
    public String getHighestBidder(String itemId) {
        String sql = "SELECT bidder_id FROM bids WHERE item_id = ? ORDER BY bid_price DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) return rs.getString("bidder_id");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
