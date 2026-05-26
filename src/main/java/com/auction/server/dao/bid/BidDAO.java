package com.auction.server.dao.bid;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.auction.server.database.DatabaseConnection;

/**
 * Lớp điều hướng và thao tác dữ liệu (DAO) cốt lõi của tính năng Đấu giá.
 * Tập trung xử lý các hành động kiểm tra và ghi nhận lượt đặt giá trực tiếp (Realtime).
 */
public class BidDAO {

    /**
     * BỔ SUNG: Cấu trúc bản ghi Lịch sử đặt giá (BidRecord) dữ liệu sạch.
     * Giúp đồng bộ dữ liệu gửi/nhận giữa Controller, Service và giao diện Client.
     */
    public record BidRecord(String id, String itemId, String bidderId, double bidPrice, String bidTime) {}

    /**
     * Thực hiện đặt giá mới cho một sản phẩm. Ghi nhận thông tin trực tiếp vào bảng `bids`.
     * @param itemId Mã định danh sản phẩm/mục đấu giá
     * @param bidderId Mã định danh người tham gia đặt giá
     * @param bidPrice Số tiền người dùng muốn trả
     * @return true nếu ghi nhận thành công (số dòng ảnh hưởng > 0), ngược lại false
     */
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

    /**
     * Lấy ra mức giá cao nhất hiện tại của một sản phẩm trong bảng `bids`.
     * @param itemId Mã sản phẩm cần kiểm tra
     * @return Số tiền lớn nhất tìm thấy, hoặc 0 nếu chưa có ai đặt giá hay lỗi
     */
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

    /**
     * Xác định người đang tạm thời dẫn đầu (đặt giá cao nhất) của sản phẩm.
     * Sắp xếp giá giảm dần và chỉ lấy 1 bản ghi đầu tiên.
     * @param itemId Mã định danh sản phẩm
     * @return Mã định danh (ID) của người đặt giá cao nhất, hoặc null nếu chưa có ai đặt giá
     */
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

    /**
     * BỔ SUNG KHUYẾT THIẾU CỐT LÕI: Lấy lịch sử tất cả các lượt đặt giá của một sản phẩm.
     * Sắp xếp theo mốc thời gian hoặc mức giá mới nhất hiển thị lên đầu phòng chơi.
     * * @param itemId Mã định danh sản phẩm cần lấy lịch sử
     * @return Danh sách các bản ghi BidRecord danh định rõ ràng
     */
    public List<BidRecord> getBidHistory(String itemId) {
        List<BidRecord> history = new ArrayList<>();
        // Truy vấn sắp xếp giá cược giảm dần để người xem thấy ai đang đứng đầu nhanh nhất
        String sql = "SELECT id, item_id, bidder_id, bid_price, bid_time FROM bids WHERE item_id = ? ORDER BY bid_price DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String bId = rs.getString("bidder_id");
                    double price = rs.getDouble("bid_price");
                    // Đề phòng trường hợp cột bid_time lưu đối tượng Timestamp, chuyển an toàn sang chuỗi hiển thị
                    String time = rs.getTimestamp("bid_time") != null ? rs.getTimestamp("bid_time").toString() : "";
                    
                    history.add(new BidRecord(id, itemId, bId, price, time));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return history;
    }

    /**
     * Lưu thông tin lượt đặt giá đồng thời sinh mã ngẫu nhiên UUID làm khóa chính.
     * Hàm này phục vụ bổ sung cho logic của tầng nghiệp vụ `AuctionService.placeBid()`.
     */
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

    /**
     * Cập nhật thông tin giá hiện tại và người đặt giá mới nhất trực tiếp vào bảng `products`.
     * Giúp đồng bộ thông tin hiển thị tổng quan của sản phẩm một cách nhanh chóng.
     * @return true nếu cập nhật trạng thái sản phẩm thành công
     */
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

    /**
     * Lấy giá hiện tại của sản phẩm. Nếu sản phẩm chưa từng được đấu giá (`current_bid` bị NULL), 
     * hàm sẽ tự động lấy giá khởi điểm (`starting_price`) thay thế nhờ hàm COALESCE trong SQL.
     */
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
}