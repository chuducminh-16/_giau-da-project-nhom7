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

    // Đặt giá mới vào bảng bids
    public boolean placeBid(String itemId, String bidderId, double bidPrice) {
        String sql = "INSERT INTO bids (item_id, bidder_id, bid_price, bid_time) VALUES (?, ?, ?, NOW())";
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

    // Xem lịch sử đặt giá của 1 sản phẩm — sắp xếp theo thời gian MỚI NHẤT trước
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
    // BỔ SUNG — phục vụ AuctionService.placeBid()
    // ══════════════════════════════════════════

    public boolean save(String productId, String bidderId, double amount) {
        String sql = "INSERT INTO bids (id, item_id, bidder_id, bid_price) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, productId);
            ps.setString(3, bidderId);
            ps.setDouble(4, amount);
            ps.executeUpdate();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updateCurrentBid(String productId, double amount, String bidderId) {
        String sql = "UPDATE products SET current_bid = ?, current_bidder = ? WHERE id = ?";
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

    public double getCurrentBid(String productId) {
        String sql = "SELECT COALESCE(current_bid, starting_price) FROM products WHERE id = ?";
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
     * Lấy lịch sử bid của 1 sản phẩm dưới dạng List<BidRecord>.
     *
     * FIX QUAN TRỌNG:
     *   - ORDER BY b.bid_time DESC  → mới nhất trước (client sẽ đảo lại khi vẽ chart)
     *   - Giới hạn 50 bản ghi gần nhất để tránh quá tải
     *
     * KEY JSON KHI GSON SERIALIZE BidRecord:
     *   "bidderId"  → bidder_id
     *   "username"  → username
     *   "amount"    → bid_price   ← client đọc key "amount"
     *   "createdAt" → bid_time    ← client đọc key "createdAt" hoặc "bidTime"
     *
     * Client LiveBiddingController (document 69) đã xử lý cả 2 key:
     *   safeStr(row, "bidTime", safeStr(row, "createdAt", ""))
     *   safeDouble(row, "bidPrice") > 0 ? ... : safeDouble(row, "amount")
     */
    public List<BidRecord> getBidRecords(String productId) {
        List<BidRecord> list = new ArrayList<>();
        String sql = "SELECT b.bidder_id, u.username, b.bid_price, b.bid_time" +
                " FROM bids b" +
                " JOIN users u ON b.bidder_id = u.id" +
                " WHERE b.item_id = ?" +
                " ORDER BY b.bid_time DESC" +   // mới nhất trước, client đảo khi vẽ chart
                " LIMIT 50";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new BidRecord(
                        rs.getString("bidder_id"),
                        rs.getString("username"),
                        rs.getDouble("bid_price"),  // → field "amount" trong record
                        rs.getString("bid_time")    // → field "createdAt" trong record
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ── Records ───────────────────────────────────────────────────────────

    /**
     * BidRecord — dữ liệu 1 lần đặt giá.
     *
     * Gson serialize field names thành JSON key:
     *   bidderId  → "bidderId"
     *   username  → "username"
     *   amount    → "amount"    ← client parse bằng key này
     *   createdAt → "createdAt" ← client parse bằng key này
     *
     * QUAN TRỌNG: KHÔNG đổi tên field vì sẽ ảnh hưởng JSON key gửi đến client.
     */
    public record BidRecord(
            String bidderId,
            String username,
            double amount,      // map từ bid_price trong DB
            String createdAt    // map từ bid_time trong DB
    ) {}

    public record BidHistoryRecord(
            String  bidderId,
            String  username,
            double  amount,
            String  createdAt,
            String  itemName,
            String  auctionStatus,
            boolean isWinner
    ) {}

    /**
     * Lịch sử bid của 1 bidder — dùng cho BidHistoryController.
     * Lấy lần đặt giá cao nhất của bidder cho mỗi sản phẩm.
     * isWinner = true nếu giá của bidder = current_price VÀ phiên đã FINISHED/PAID.
     */
    public List<BidHistoryRecord> getBidHistoryByBidder(String bidderId) {
        List<BidHistoryRecord> list = new ArrayList<>();
        String sql =
                "SELECT b.bidder_id, u.username, " +
                        "       MAX(b.bid_price) AS bid_price, " +
                        "       MAX(b.bid_time)  AS bid_time, " +
                        "       i.name           AS item_name, " +
                        "       a.status         AS auction_status, " +
                        "       a.current_price  AS current_price " +
                        "FROM bids b " +
                        "JOIN users u ON b.bidder_id = u.id " +
                        "JOIN items i ON b.item_id = i.id " +
                        "LEFT JOIN auctions a ON a.item_id = b.item_id " +
                        "WHERE b.bidder_id = ? " +
                        "GROUP BY b.item_id, i.name, a.status, a.current_price, b.bidder_id, u.username " +
                        "ORDER BY bid_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                double myBid        = rs.getDouble("bid_price");
                double currentPrice = rs.getDouble("current_price");
                String auctionStatus = rs.getString("auction_status");

                // Thắng nếu giá của mình ≈ giá cao nhất VÀ phiên đã kết thúc
                boolean isWinner = Math.abs(myBid - currentPrice) < 0.01
                        && ("FINISHED".equals(auctionStatus) || "PAID".equals(auctionStatus));

                list.add(new BidHistoryRecord(
                        rs.getString("bidder_id"),
                        rs.getString("username"),
                        myBid,
                        rs.getString("bid_time"),
                        rs.getString("item_name"),
                        auctionStatus != null ? auctionStatus : "UNKNOWN",
                        isWinner
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
