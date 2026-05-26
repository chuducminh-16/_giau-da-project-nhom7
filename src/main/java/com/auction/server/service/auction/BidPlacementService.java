package com.auction.server.service.auction;

import com.auction.server.database.DatabaseConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BidPlacementService - Chịu trách nhiệm cốt lõi về logic đặt giá (Place Bid).
 * Đảm bảo tính nhất quán dữ liệu dưới tải cao, chống race-condition và kỹ thuật anti-sniping.
 */
public class BidPlacementService {

    private static final int SNIPE_WINDOW_SECONDS = 60; // Khoảng thời gian nhạy cảm (1 phút cuối)
    private static final int SNIPE_EXTEND_SECONDS = 60; // Thời gian gia hạn thêm nếu có người snipe (1 phút)

    // Khởi tạo bản đồ Lock để quản lý khóa độc quyền cho từng sản phẩm riêng biệt
    private static final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    /**
     * Cấp khóa Lock tương ứng với từng Product ID (Thread-safe)
     */
    private static ReentrantLock getLock(String productId) {
        return lockMap.computeIfAbsent(productId, id -> new ReentrantLock(true)); // Khóa công bằng (Fair Lock)
    }

    /**
     * Xử lý đặt giá chính thức, bọc trong một ReentrantLock ở tầng ứng dụng
     */
    public BidOutcome placeBid(String productId, String bidderId, double amount) {
        ReentrantLock lock = getLock(productId);
        lock.lock(); // Chặn các Thread khác đặt giá cùng sản phẩm tại cùng thời điểm
        try {
            return placeBidInTransaction(productId, bidderId, amount);
        } finally {
            lock.unlock(); // Giải phóng khóa cho Thread tiếp theo vào hàng đợi
        }
    }

    /**
     * Thực thi toàn bộ chuỗi kiểm tra và ghi dữ liệu đặt giá trong duy nhất 1 DB Transaction
     */
    private BidOutcome placeBidInTransaction(String productId, String bidderId, double amount) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // ── BƯỚC 1: Đọc thông tin phòng đấu giá hiện tại và khóa dòng dữ liệu bằng SELECT FOR UPDATE ──
                BidCheckResult check = readForUpdate(conn, productId);

                if (check == null) {
                    conn.rollback();
                    return new BidOutcome(BidResult.AUCTION_NOT_FOUND, 0, "Khong tim thay phien dau gia.", null);
                }

                // Kiểm tra trạng thái phiên đấu giá
                if (!"OPEN".equals(check.status) && !"RUNNING".equals(check.status)) {
                    conn.rollback();
                    return new BidOutcome(BidResult.AUCTION_ENDED, 0, "Phien dau gia da ket thuc.", null);
                }

                // Kiểm tra xem phòng đấu giá đã quá giờ đóng cửa chưa
                if (check.endTime != null && LocalDateTime.now().isAfter(check.endTime)) {
                    conn.rollback();
                    return new BidOutcome(BidResult.AUCTION_ENDED, 0, "Phien dau gia da het gio.", null);
                }

                // Quy tắc kinh doanh: Chủ phòng (Seller) không được tự nâng giá sản phẩm của chính mình
                if (bidderId.equals(check.sellerId)) {
                    conn.rollback();
                    return new BidOutcome(BidResult.ERROR, 0, "Seller khong the tu dat gia vao phien cua minh.", null);
                }

                // ── BƯỚC 2: Kiểm tra giá trị đặt cược với nguồn dữ liệu duy nhất đúng (currentPrice từ DB Lock) ──
                if (amount <= check.currentPrice) {
                    conn.rollback();
                    return new BidOutcome(BidResult.PRICE_TOO_LOW, check.currentPrice,
                            String.format("Gia phai cao hon %.0f VND.", check.currentPrice), null);
                }

                // ── BƯỚC 3: Thêm bản ghi mới vào bảng lịch sử bids ──
                boolean bidSaved = insertBid(conn, productId, bidderId, amount);
                if (!bidSaved) {
                    conn.rollback();
                    return new BidOutcome(BidResult.ERROR, check.currentPrice, "Loi luu bid.", null);
                }

                // ── BƯỚC 4: Cập nhật giá mới đồng thời sang cả hai bảng auctions và items ──
                updateAuctionPrice(conn, check.auctionId, amount);
                updateItemPrice(conn, productId, amount);

                // ── BƯỚC 5: Xử lý cơ chế Anti-sniping (Chống đặt giá lén giây cuối) ──
                String newEndTimeStr = null;
                if (check.endTime != null) {
                    long secondsLeft = java.time.Duration.between(LocalDateTime.now(), check.endTime).getSeconds();
                    
                    // Nếu thời gian còn lại ít hơn hoặc bằng cửa sổ thời gian quy định (60 giây)
                    if (secondsLeft <= SNIPE_WINDOW_SECONDS) {
                        extendEndTime(conn, check.auctionId, SNIPE_EXTEND_SECONDS); // Gia hạn thêm thời gian phòng
                        newEndTimeStr = getEndTime(conn, check.auctionId);         // Lấy mốc thời gian mới vừa cập nhật
                        System.out.printf("[BidPlacementService] Anti-sniping: phien #%d gia han +%ds%n",
                                check.auctionId, SNIPE_EXTEND_SECONDS);
                    }
                }

                // ── BƯỚC 6: Commit toàn bộ dữ liệu khi tất cả các bước trên đều chạy trơn tru ──
                conn.commit();
                System.out.printf("[BidPlacementService] BID OK: product=%s bidder=%s amount=%.0f%n",
                        productId, bidderId, amount);

                return new BidOutcome(BidResult.SUCCESS, amount, "Dat gia thanh cong!", newEndTimeStr);

            } catch (Exception e) {
                conn.rollback(); // Hủy bỏ và khôi phục trạng thái nếu xảy ra bất kỳ lỗi runtime nào
                System.err.println("[BidPlacementService] placeBid rollback: " + e.getMessage());
                e.printStackTrace();
                return new BidOutcome(BidResult.ERROR, 0, "Loi he thong: " + e.getMessage(), null);
            }
        } catch (Exception e) {
            System.err.println("[BidPlacementService] Loi mo connection: " + e.getMessage());
            return new BidOutcome(BidResult.ERROR, 0, "Loi ket noi database.", null);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Tầng SQL TRỢ THỦ - Sử dụng chung Connection từ Transaction chuyển xuống
    // ─────────────────────────────────────────────────────────────────

    private BidCheckResult readForUpdate(Connection conn, String productId) throws Exception {
        String sql = "SELECT a.id, a.seller_id, a.status, a.end_time, a.current_price " +
                     "FROM auctions a " +
                     "WHERE a.item_id = ? AND a.status IN ('OPEN','RUNNING') " +
                     "ORDER BY a.id DESC LIMIT 1 " +
                     "FOR UPDATE"; // Khóa chặt hàng ghi dữ liệu này cho tới khi transaction kết thúc
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long         auctionId    = rs.getLong("id");
                String        sellerId     = rs.getString("seller_id");
                String        status       = rs.getString("status");
                double        currentPrice = rs.getDouble("current_price");
                String        endTimeStr   = rs.getString("end_time");
                LocalDateTime endTime      = null;
                if (endTimeStr != null) {
                    try { endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T")); }
                    catch (Exception ignored) {}
                }
                return new BidCheckResult(auctionId, sellerId, status, currentPrice, endTime);
            }
        }
    }

    private boolean insertBid(Connection conn, String itemId, String bidderId, double amount) throws Exception {
        String sql = "INSERT INTO bids (item_id, bidder_id, bid_price, bid_time) VALUES (?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, bidderId);
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        }
    }

    private void updateAuctionPrice(Connection conn, long auctionId, double newPrice) throws Exception {
        String sql = "UPDATE auctions SET current_price = ?, status = 'RUNNING' WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setLong(2, auctionId);
            ps.executeUpdate();
        }
    }

    private void updateItemPrice(Connection conn, String itemId, double newPrice) throws Exception {
        String sql = "UPDATE items SET current_price = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    private void extendEndTime(Connection conn, long auctionId, int extraSeconds) throws Exception {
        String sql = "UPDATE auctions SET end_time = DATE_ADD(end_time, INTERVAL ? SECOND) WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, extraSeconds);
            ps.setLong(2, auctionId);
            ps.executeUpdate();
        }
    }

    private String getEndTime(Connection conn, long auctionId) throws Exception {
        String sql = "SELECT end_time FROM auctions WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("end_time") : null;
            }
        }
    }

    // Các enum và DTO bổ trợ phục vụ giao tiếp kết quả
    public enum BidResult { SUCCESS, PRICE_TOO_LOW, AUCTION_ENDED, AUCTION_NOT_FOUND, ERROR }
    public record BidOutcome(BidResult result, double newBid, String message, String newEndTime) {}
    private record BidCheckResult(long auctionId, String sellerId, String status, double currentPrice, LocalDateTime endTime) {}
}