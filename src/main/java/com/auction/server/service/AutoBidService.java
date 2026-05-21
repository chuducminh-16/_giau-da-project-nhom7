package com.auction.server.service;

import com.auction.server.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dịch vụ Auto-Bidding (Đấu giá tự động) — chức năng nâng cao.
 *
 * Logic:
 *   - Bidder đăng ký maxBid cho 1 phiên
 *   - Khi có bid mới từ đối thủ → hệ thống tự động đặt giá thay cho người đăng ký
 *   - Không vượt quá maxBid
 *   - Ưu tiên theo thứ tự đăng ký (ai đăng ký trước thì ưu tiên hơn nếu cùng maxBid)
 *   - Xử lý xung đột: dùng PriorityQueue + lock per auction
 *
 * Bảng DB cần thêm (chạy script SQL bên dưới):
 *   CREATE TABLE auto_bids (
 *     id          INT AUTO_INCREMENT PRIMARY KEY,
 *     item_id     VARCHAR(50) NOT NULL,
 *     bidder_id   VARCHAR(50) NOT NULL,
 *     max_bid     DECIMAL(15,2) NOT NULL,
 *     increment   DECIMAL(15,2) NOT NULL DEFAULT 100,
 *     registered_at DATETIME DEFAULT CURRENT_TIMESTAMP,
 *     active      BOOLEAN DEFAULT TRUE,
 *     FOREIGN KEY (item_id)   REFERENCES items(id),
 *     FOREIGN KEY (bidder_id) REFERENCES users(id),
 *     UNIQUE KEY uq_auto_bid (item_id, bidder_id)
 *   );
 */
public class AutoBidService {

    private final AuctionService auctionService;

    // Lock per auctionItemId để tránh race condition khi trigger auto-bid
    private final ConcurrentHashMap<String, Object> autoLocks = new ConcurrentHashMap<>();

    public AutoBidService(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    // ═══════════════════════════════════════════════════════
    // ĐĂNG KÝ AUTO-BID
    // ═══════════════════════════════════════════════════════

    /**
     * Bidder đăng ký auto-bid cho 1 phiên.
     *
     * @param itemId    ID sản phẩm
     * @param bidderId  ID bidder
     * @param maxBid    Giá tối đa không muốn vượt
     * @param increment Bước tự động tăng mỗi lần
     * @return true nếu đăng ký thành công
     */
    public boolean registerAutoBid(String itemId, String bidderId,
                                   double maxBid, double increment) {
        String sql = "INSERT INTO auto_bids (item_id, bidder_id, max_bid, increment) "
                   + "VALUES (?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE max_bid = ?, increment = ?, active = TRUE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, bidderId);
            ps.setDouble(3, maxBid);
            ps.setDouble(4, increment);
            ps.setDouble(5, maxBid);
            ps.setDouble(6, increment);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Hủy auto-bid (bidder tự hủy hoặc khi maxBid bị vượt qua).
     */
    public boolean cancelAutoBid(String itemId, String bidderId) {
        String sql = "UPDATE auto_bids SET active = FALSE "
                   + "WHERE item_id = ? AND bidder_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, bidderId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════
    // TRIGGER AUTO-BID sau khi có bid mới
    // ═══════════════════════════════════════════════════════

    /**
     * Gọi sau mỗi bid thành công để trigger auto-bid của các đối thủ.
     *
     * @param itemId       ID sản phẩm vừa có bid mới
     * @param newPrice     Giá hiện tại mới (sau bid vừa xong)
     * @param lastBidderId ID người vừa bid (không trigger auto-bid của chính họ)
     * @return AutoBidResult nếu có auto-bid được trigger, null nếu không
     */
    public AutoBidResult triggerAutoBid(String itemId, double newPrice, String lastBidderId) {
        // Một lock per item để tránh 2 thread cùng trigger auto-bid cho 1 phiên
        Object lock = autoLocks.computeIfAbsent(itemId, k -> new Object());
        synchronized (lock) {
            // Lấy tất cả auto-bid còn active, sắp xếp ưu tiên:
            // 1. maxBid cao hơn trước
            // 2. Nếu cùng maxBid → ai đăng ký sớm hơn (registered_at nhỏ hơn)
            List<AutoBidEntry> candidates = getActiveCandidates(itemId, lastBidderId, newPrice);

            if (candidates.isEmpty()) return null;

            // PriorityQueue: ưu tiên maxBid cao nhất, nếu bằng nhau thì registered_at sớm hơn
            PriorityQueue<AutoBidEntry> pq = new PriorityQueue<>(candidates.size(),
                    Comparator.comparingDouble(AutoBidEntry::maxBid).reversed()
                              .thenComparingLong(AutoBidEntry::registeredAt));

            pq.addAll(candidates);

            AutoBidEntry winner = pq.poll();
            if (winner == null) return null;

            // Tính giá auto-bid = newPrice + increment của winner
            double autoBidAmount = newPrice + winner.increment();

            // Kiểm tra không vượt maxBid
            if (autoBidAmount > winner.maxBid()) {
                // Winner không đủ → hủy auto-bid của họ và thử người tiếp theo
                cancelAutoBid(itemId, winner.bidderId());
                return null; // không trigger thêm để tránh cascade vô hạn
            }

            // Thực hiện auto-bid
            AuctionService.BidOutcome outcome =
                    auctionService.placeBid(itemId, winner.bidderId(), autoBidAmount);

            if (outcome.result() == AuctionService.BidResult.SUCCESS) {
                System.out.printf("[AutoBid] %s auto-bid %.0f cho %s%n",
                        winner.bidderId(), autoBidAmount, itemId);
                return new AutoBidResult(
                        winner.bidderId(), autoBidAmount, outcome.newEndTime());
            }

            return null;
        }
    }

    // ═══════════════════════════════════════════════════════
    // QUERY — lấy auto-bid candidates từ DB
    // ═══════════════════════════════════════════════════════

    private List<AutoBidEntry> getActiveCandidates(String itemId,
                                                    String excludeBidderId,
                                                    double currentPrice) {
        List<AutoBidEntry> list = new ArrayList<>();
        String sql = "SELECT bidder_id, max_bid, increment, "
                   + "UNIX_TIMESTAMP(registered_at) as reg_ts "
                   + "FROM auto_bids "
                   + "WHERE item_id = ? AND active = TRUE "
                   + "  AND bidder_id != ? "
                   + "  AND max_bid > ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, excludeBidderId);
            ps.setDouble(3, currentPrice);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new AutoBidEntry(
                        rs.getString("bidder_id"),
                        rs.getDouble("max_bid"),
                        rs.getDouble("increment"),
                        rs.getLong("reg_ts")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════
    // INNER TYPES
    // ═══════════════════════════════════════════════════════

    /** Dữ liệu 1 auto-bid entry từ DB. */
    public record AutoBidEntry(
            String bidderId,
            double maxBid,
            double increment,
            long   registeredAt   // Unix timestamp để so sánh thứ tự đăng ký
    ) {}

    /**
     * Kết quả khi auto-bid được trigger thành công.
     * Trả về để ClientHandler broadcast cho tất cả client.
     */
    public record AutoBidResult(
            String bidderId,
            double newBid,
            String newEndTime    // null nếu không gia hạn
    ) {}
}
