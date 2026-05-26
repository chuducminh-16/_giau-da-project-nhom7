package com.auction.server.dao.bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.auction.server.database.DatabaseConnection;

/**
 * Lớp chuyên trách xử lý truy vấn Lịch sử đấu giá (Bid History).
 * Giúp giải tải và cô lập các câu lệnh SQL liên kết nhiều bảng phức tạp ra khỏi tầng giao dịch trực tiếp.
 */
public class BidHistoryDAO {

    // ── Các cấu trúc bản ghi dữ liệu (Records) dùng chung cho tầng thống kê lịch sử ──

    /**
     * BidRecord — Dữ liệu phản hồi của một lượt đặt giá cụ thể trên một sản phẩm.
     * Tên các trường được giữ nguyên để GSON đóng gói thành các trường JSON tương ứng gửi về Client:
     * - bidderId  -> "bidderId"
     * - username  -> "username"
     * - amount    -> "amount"     (Client đọc trường này)
     * - createdAt -> "createdAt"  (Client đọc trường này)
     */
    public record BidRecord(
            String bidderId,
            String username,
            double amount,      // Ánh xạ từ cột dữ liệu `bid_price` trong DB
            String createdAt    // Ánh xạ từ cột dữ liệu `bid_time` trong DB
    ) {}

    /**
     * BidHistoryRecord — Cấu trúc dữ liệu chi tiết lịch sử tổng thể của một cá nhân đi đấu giá.
     */
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
     * Lấy toàn bộ lịch sử đặt giá của một sản phẩm dưới dạng danh sách bản đồ thông tin (Map).
     * Kết quả được sắp xếp theo thời gian mới nhất lên đầu tiên.
     */
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

    /**
     * Lấy danh sách lịch sử đặt giá của sản phẩm dưới cấu trúc dữ liệu đối tượng tĩnh `BidRecord`.
     * Giới hạn tối đa 50 bản ghi gần nhất để tối ưu hiệu năng đường truyền mạng và tránh quá tải hệ thống.
     * * Lưu ý: Kết quả trả về ưu tiên lượt đặt mới nhất đứng trước, khi Client nhận được 
     * sẽ thực hiện đảo ngược mảng để phục vụ logic vẽ đồ thị diễn biến giá (Chart).
     */
    public List<BidRecord> getBidRecords(String productId) {
        List<BidRecord> list = new ArrayList<>();
        String sql = "SELECT b.bidder_id, u.username, b.bid_price, b.bid_time" +
                " FROM bids b" +
                " JOIN users u ON b.bidder_id = u.id" +
                " WHERE b.item_id = ?" +
                " ORDER BY b.bid_time DESC" + 
                " LIMIT 50";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, productId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new BidRecord(
                        rs.getString("bidder_id"),
                        rs.getString("username"),
                        rs.getDouble("bid_price"),  // Chuyển đổi thành trường "amount" trong Object
                        rs.getString("bid_time")    // Chuyển đổi thành trường "createdAt" trong Object
                ));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * Lấy lịch sử đấu giá của một người dùng cụ thể phục vụ màn hình `BidHistoryController` cá nhân.
     * Hàm thực hiện gộp nhóm dữ liệu (GROUP BY) để lấy ra lượt đặt giá cao nhất của người dùng này trên từng sản phẩm.
     * * Thuật toán xác định người thắng cuộc (isWinner):
     * Nếu giá người dùng này đặt xấp xỉ bằng giá cao nhất hiện tại của phiên đấu giá đó (sai số nhỏ hơn 0.01)
     * VÀ trạng thái của phiên đấu giá đã kết thúc thành công (`FINISHED` hoặc `PAID`), người đó được coi là Thắng cuộc.
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

                // Biểu thức logic kiểm tra điều kiện thắng đấu giá
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