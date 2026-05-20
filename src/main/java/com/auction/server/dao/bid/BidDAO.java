package com.auction.server.dao.bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.auction.server.database.DatabaseConnection;

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
        String sql = "SELECT bidder_id FROM bids WHERE item_id = ?" +
                " ORDER BY bid_price DESC LIMIT 1";
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

    // ══════════════════════════════════════════
    // BỔ SUNG MỚI — phục vụ AuctionService.placeBid()
    // ══════════════════════════════════════════

    /**
     * Lưu 1 lượt bid có id riêng vào DB.
     * Khác placeBid() cũ ở chỗ: tự sinh UUID, dùng tên cột id thay vì auto-increment.
     * Dùng trong AuctionService.placeBid() sau khi đã lock.
     */
    public boolean save(String productId, String bidderId, double amount) {
        String sql = "INSERT INTO bids (id, item_id, bidder_id, bid_price)" +
                " VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, productId);   // map sang item_id trong DB cũ
            ps.setString(3, bidderId);
            ps.setDouble(4, amount);
            ps.executeUpdate();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Cập nhật current_bid và current_bidder trên bảng products.
     * Gọi ngay sau save() trong cùng 1 lock để đảm bảo nhất quán.
     */
    public boolean updateCurrentBid(String productId, double amount,
                                    String bidderId) {
        String sql = "UPDATE products" +
                " SET current_bid = ?, current_bidder = ?" +
                " WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setDouble(1, amount);
            ps.setString(2, bidderId);
            ps.setString(3, productId);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lấy giá hiện tại của sản phẩm từ cột current_bid.
     * Nếu chưa có bid nào thì fallback về starting_price.
     * Dùng trong AuctionService.placeBid() để validate trong lock.
     */
    public double getCurrentBid(String productId) {
        String sql = "SELECT COALESCE(current_bid, starting_price)" +
                " FROM products WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, productId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble(1);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Lấy lịch sử bid dạng List<BidRecord> — dùng cho ClientHandler
     * khi client gửi GET_BID_HISTORY.
     * Trả về tối đa 20 bid gần nhất theo thứ tự mới nhất trước.
     */
    public List<BidRecord> getBidRecords(String productId) {
        List<BidRecord> list = new ArrayList<>();
        String sql = "SELECT b.bidder_id, u.username," +
                "       b.bid_price, b.bid_time" +
                " FROM bids b" +
                " JOIN users u ON b.bidder_id = u.id" +
                " WHERE b.item_id = ?" +
                " ORDER BY b.bid_price DESC" +
                " LIMIT 20";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new BidRecord(
                        rs.getString("bidder_id"),
                        rs.getString("username"),
                        rs.getDouble("bid_price"),
                        rs.getString("bid_time")
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ── Record kết quả truy vấn — dùng để serialize sang JSON ──
    public record BidRecord(
            String bidderId,
            String username,
            double amount,
            String createdAt
    ) {}
}