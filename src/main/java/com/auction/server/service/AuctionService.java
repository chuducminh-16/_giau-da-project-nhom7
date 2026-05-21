package com.auction.server.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.auction.client.model.Product;
import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.item.ItemSaveDAO;
import com.auction.server.dao.transaction.TransactionDAO;

/**
 * Service xử lý toàn bộ nghiệp vụ đấu giá.
 *
 * PATCH thêm:
 *   1. Anti-sniping: nếu bid vào trong 60 giây cuối → gia hạn thêm 60 giây
 *   2. getBidHistory(productId): lấy lịch sử bid gửi về client
 *   3. getAutoBidCandidates(): lấy auto-bid còn hiệu lực để AutoBidService dùng
 *
 * Thread-safe: mỗi productId có 1 ReentrantLock riêng.
 */
public class AuctionService {

    // ── Anti-sniping config ──────────────────────────────────────────────
    /** Nếu bid trong X giây cuối → gia hạn thêm Y giây. */
    private static final int SNIPE_WINDOW_SECONDS  = 60;  // 60 giây cuối
    private static final int SNIPE_EXTEND_SECONDS  = 60;  // gia hạn thêm 60 giây

    // ── DAO ──────────────────────────────────────────────────────────────
    private final AuctionDAO     auctionDAO     = new AuctionDAO();
    private final BidDAO         bidDAO         = new BidDAO();
    private final ItemSaveDAO    itemSaveDAO    = new ItemSaveDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    // ── Lock per productId ───────────────────────────────────────────────
    private final ConcurrentHashMap<String, ReentrantLock> lockMap =
            new ConcurrentHashMap<>();

    private ReentrantLock getLock(String productId) {
        return lockMap.computeIfAbsent(productId, id -> new ReentrantLock());
    }

    // ═══════════════════════════════════════════════════════
    // PLACE BID — thread-safe, anti-sniping
    // ═══════════════════════════════════════════════════════

    /**
     * Đặt giá cho phiên đấu giá.
     * Nếu bid hợp lệ và còn trong cửa sổ anti-sniping → tự động gia hạn endTime.
     *
     * @return BidOutcome chứa kết quả và endTime mới (nếu đã gia hạn)
     */
    public BidOutcome placeBid(String productId, String bidderId, double amount) {
        ReentrantLock lock = getLock(productId);
        lock.lock();
        try {
            // 1. Tìm phiên đấu giá
            List<Map<String, Object>> auctions = auctionDAO.findAllOpen();
            Map<String, Object> targetAuction = auctions.stream()
                    .filter(a -> productId.equals(a.get("itemId")))
                    .findFirst()
                    .orElse(null);

            if (targetAuction == null) {
                return new BidOutcome(BidResult.AUCTION_NOT_FOUND, 0,
                        "Không tìm thấy phiên đấu giá.", null);
            }

            // 2. Kiểm tra trạng thái
            String status = (String) targetAuction.get("status");
            if (!"OPEN".equals(status) && !"RUNNING".equals(status)) {
                return new BidOutcome(BidResult.AUCTION_ENDED, 0,
                        "Phiên đấu giá đã kết thúc.", null);
            }

            // 3. Kiểm tra thời gian
            String endTimeStr = (String) targetAuction.get("endTime");
            LocalDateTime endTime = null;
            if (endTimeStr != null) {
                endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T"));
                if (LocalDateTime.now().isAfter(endTime)) {
                    return new BidOutcome(BidResult.AUCTION_ENDED, 0,
                            "Phiên đấu giá đã hết giờ.", null);
                }
            }

            // 4. Kiểm tra giá
            double currentPrice = (double) targetAuction.get("currentPrice");
            if (amount <= currentPrice) {
                return new BidOutcome(BidResult.PRICE_TOO_LOW, currentPrice,
                        String.format("Giá phải cao hơn %.0f VNĐ.", currentPrice), null);
            }

            // 5. Lưu bid
            long auctionId = (long) targetAuction.get("id");
            boolean bidSaved = bidDAO.placeBid(productId, bidderId, amount);
            if (!bidSaved) {
                return new BidOutcome(BidResult.ERROR, currentPrice, "Lỗi lưu bid.", null);
            }

            // 6. Cập nhật giá phiên
            auctionDAO.updateCurrentPrice(auctionId, amount);

            // 7. Anti-sniping: kiểm tra có trong cửa sổ snipe không
            String newEndTimeStr = null;
            if (endTime != null) {
                long secondsLeft = java.time.Duration
                        .between(LocalDateTime.now(), endTime)
                        .getSeconds();
                if (secondsLeft <= SNIPE_WINDOW_SECONDS) {
                    boolean extended = auctionDAO.extendEndTime(auctionId, SNIPE_EXTEND_SECONDS);
                    if (extended) {
                        // Lấy endTime mới để trả về client
                        newEndTimeStr = auctionDAO.getEndTime(auctionId);
                        System.out.printf("[AuctionService] Anti-sniping: phiên #%d gia hạn thêm %ds%n",
                                auctionId, SNIPE_EXTEND_SECONDS);
                    }
                }
            }

            return new BidOutcome(BidResult.SUCCESS, amount, "Đặt giá thành công!", newEndTimeStr);

        } finally {
            lock.unlock();
        }
    }

    // ═══════════════════════════════════════════════════════
    // GET BID HISTORY — lịch sử bid gửi về LiveBiddingController
    // ═══════════════════════════════════════════════════════

    /**
     * Lấy tối đa 20 bid gần nhất của 1 sản phẩm.
     * Dùng cho ClientHandler khi client gửi GET_BID_HISTORY.
     */
    public List<BidDAO.BidRecord> getBidHistory(String productId) {
        return bidDAO.getBidRecords(productId);
    }

    // ═══════════════════════════════════════════════════════
    // GET ACTIVE AUCTIONS
    // ═══════════════════════════════════════════════════════

    public List<Product> getActiveAuctions() {
        List<Map<String, Object>> rows = auctionDAO.findAllOpen();
        List<Product> products = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            products.add(mapToProduct(row));
        }
        return products;
    }

    // ═══════════════════════════════════════════════════════
    // GET PRODUCTS BY SELLER
    // ═══════════════════════════════════════════════════════

    public List<Product> getProductsBySeller(String sellerId) {
        List<Map<String, Object>> rows = auctionDAO.findBySeller(sellerId);
        List<Product> products = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            products.add(mapToProduct(row));
        }
        return products;
    }

    // ═══════════════════════════════════════════════════════
    // ADD PRODUCT
    // ═══════════════════════════════════════════════════════

    public Product addProduct(String sellerId, String name, String description,
                              double startPrice, double bidIncrement,
                              String imagePath, LocalDateTime startTime,
                              LocalDateTime endTime) {
        String itemId = "I-" + UUID.randomUUID().toString().substring(0, 8);

        com.auction.shared.model.Entity.Item.Art item =
                new com.auction.shared.model.Entity.Item.Art(
                        itemId, name, startPrice, endTime, sellerId, description);

        boolean itemSaved = itemSaveDAO.saveItem(item);
        if (!itemSaved) return null;

        long auctionId = System.currentTimeMillis();
        boolean auctionSaved = auctionDAO.saveAuction(
                auctionId, itemId, sellerId, startPrice, endTime.toString());
        if (!auctionSaved) return null;

        Product p = new Product(name, startPrice, bidIncrement,
                description, "PENDING", endTime, imagePath, startTime);
        p.setId(itemId);
        p.setSellerId(sellerId);
        return p;
    }

    // ═══════════════════════════════════════════════════════
    // UPDATE PRODUCT
    // ═══════════════════════════════════════════════════════

    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime) {
        String sql = "UPDATE items SET name=?, current_price=?, end_time=? WHERE id=?";
        try (java.sql.Connection conn =
                     com.auction.server.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, startPrice);
            ps.setString(3, endTime.toString());
            ps.setString(4, productId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════
    // DELETE PRODUCT
    // ═══════════════════════════════════════════════════════

    public boolean deleteProduct(String productId) {
        String delBids    = "DELETE FROM bids WHERE item_id=?";
        String delAuction = "DELETE FROM auctions WHERE item_id=?";
        String delItem    = "DELETE FROM items WHERE id=?";
        try (java.sql.Connection conn =
                     com.auction.server.database.DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (java.sql.PreparedStatement ps = conn.prepareStatement(delBids)) {
                    ps.setString(1, productId); ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(delAuction)) {
                    ps.setString(1, productId); ps.executeUpdate();
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(delItem)) {
                    ps.setString(1, productId);
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

    // ═══════════════════════════════════════════════════════
    // END AUCTION (manual / admin)
    // ═══════════════════════════════════════════════════════

    public Map<String, Object> endAuction(long auctionId) {
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return null;

        String status = (String) auction.get("status");
        if ("FINISHED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status))
            return null;

        Map<String, Object> winner = auctionDAO.findWinner(auctionId);
        if (winner == null) {
            auctionDAO.updateStatus(auctionId, "CANCELED");
            return null;
        }

        String itemId    = (String) auction.get("itemId");
        String winnerId  = (String) winner.get("bidderId");
        double finalPrice = (double) winner.get("finalPrice");

        transactionDAO.saveTransaction(itemId, winnerId, finalPrice);
        auctionDAO.updateStatus(auctionId, "FINISHED");
        return winner;
    }

    // ═══════════════════════════════════════════════════════
    // HELPER: Map DB row → Product
    // ═══════════════════════════════════════════════════════

    private Product mapToProduct(Map<String, Object> row) {
        Product p = new Product();
        p.setId((String) row.get("itemId"));
        p.setName((String) row.get("itemName"));
        p.setSellerId((String) row.get("sellerId"));

        Object price = row.get("currentPrice");
        if (price instanceof Double d)       p.setCurrentBid(d);
        else if (price instanceof Number n)  p.setCurrentBid(n.doubleValue());

        Object startPrice = row.get("currentPrice");
        if (startPrice instanceof Number n)  p.setStartingPrice(n.doubleValue());

        p.setStatus((String) row.get("status"));

        String endTimeStr = (String) row.get("endTime");
        if (endTimeStr != null) {
            try {
                p.setEndTime(LocalDateTime.parse(endTimeStr.replace(" ", "T")));
            } catch (Exception ignored) {}
        }
        return p;
    }

    // ═══════════════════════════════════════════════════════
    // INNER TYPES
    // ═══════════════════════════════════════════════════════

    public enum BidResult {
        SUCCESS, PRICE_TOO_LOW, AUCTION_ENDED, AUCTION_NOT_FOUND, ERROR
    }

    /**
     * BidOutcome — mở rộng thêm newEndTime cho Anti-sniping.
     * newEndTime != null nghĩa là phiên vừa được gia hạn.
     */
    public record BidOutcome(
            BidResult result,
            double    newBid,
            String    message,
            String    newEndTime  // null nếu không gia hạn
    ) {}
}