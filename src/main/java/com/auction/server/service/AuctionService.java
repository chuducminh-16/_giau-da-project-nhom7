package com.auction.server.service;

import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.item.ItemSaveDAO;
import com.auction.server.dao.transaction.TransactionDAO;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AuctionService — xu ly toan bo nghiep vu dau gia.
 *
 * FIX so voi ban goc:
 *   1. placeBid(): kiem tra Seller khong duoc tu bid vao phien cua minh
 *   2. placeBid(): su dung ReentrantLock(true) — fair lock, tranh starvation
 *   3. endAuction(): tra ve Map co "itemId" de AdminController broadcast duoc
 */
public class AuctionService {

    private static final int SNIPE_WINDOW_SECONDS = 60;
    private static final int SNIPE_EXTEND_SECONDS = 60;

    private final AuctionDAO     auctionDAO     = new AuctionDAO();
    private final BidDAO         bidDAO         = new BidDAO();
    private final ItemSaveDAO    itemSaveDAO    = new ItemSaveDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    // Fair ReentrantLock per productId — tranh lost update va race condition
    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private ReentrantLock getLock(String productId) {
        return lockMap.computeIfAbsent(productId, id -> new ReentrantLock(true));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PLACE BID (thread-safe voi ReentrantLock per product)
    // ─────────────────────────────────────────────────────────────────────
    public BidOutcome placeBid(String productId, String bidderId, double amount) {
        ReentrantLock lock = getLock(productId);
        lock.lock();
        try {
            List<Map<String, Object>> auctions = auctionDAO.findAllOpen();
            Map<String, Object> targetAuction = auctions.stream()
                    .filter(a -> productId.equals(a.get("itemId")))
                    .findFirst()
                    .orElse(null);

            if (targetAuction == null)
                return new BidOutcome(BidResult.AUCTION_NOT_FOUND, 0,
                        "Khong tim thay phien dau gia.", null);

            String status = (String) targetAuction.get("status");
            if (!"OPEN".equals(status) && !"RUNNING".equals(status))
                return new BidOutcome(BidResult.AUCTION_ENDED, 0,
                        "Phien dau gia da ket thuc.", null);

            String endTimeStr = (String) targetAuction.get("endTime");
            LocalDateTime endTime = null;
            if (endTimeStr != null) {
                endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T"));
                if (LocalDateTime.now().isAfter(endTime))
                    return new BidOutcome(BidResult.AUCTION_ENDED, 0,
                            "Phien dau gia da het gio.", null);
            }

            // FIX: Seller khong duoc tu dat gia vao phien cua minh
            String sellerId = (String) targetAuction.get("sellerId");
            if (sellerId == null) sellerId = (String) targetAuction.get("seller_id");
            if (bidderId.equals(sellerId))
                return new BidOutcome(BidResult.ERROR, 0,
                        "Seller khong the tu dat gia vao phien cua minh.", null);

            Object priceObj = targetAuction.get("currentPrice");
            double currentPrice = (priceObj instanceof Number n) ? n.doubleValue() : 0;
            if (amount <= currentPrice)
                return new BidOutcome(BidResult.PRICE_TOO_LOW, currentPrice,
                        String.format("Gia phai cao hon %.0f VND.", currentPrice), null);

            long auctionId = ((Number) targetAuction.get("id")).longValue();
            boolean bidSaved = bidDAO.placeBid(productId, bidderId, amount);
            if (!bidSaved)
                return new BidOutcome(BidResult.ERROR, currentPrice, "Loi luu bid.", null);

            auctionDAO.updateCurrentPrice(auctionId, amount);

            // Anti-sniping
            String newEndTimeStr = null;
            if (endTime != null) {
                long secondsLeft = java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
                if (secondsLeft <= SNIPE_WINDOW_SECONDS) {
                    boolean extended = auctionDAO.extendEndTime(auctionId, SNIPE_EXTEND_SECONDS);
                    if (extended) {
                        newEndTimeStr = auctionDAO.getEndTime(auctionId);
                        System.out.printf("[AuctionService] Anti-sniping: phien #%d gia han +%ds%n",
                                auctionId, SNIPE_EXTEND_SECONDS);
                    }
                }
            }

            return new BidOutcome(BidResult.SUCCESS, amount, "Dat gia thanh cong!", newEndTimeStr);

        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET BID HISTORY
    // ─────────────────────────────────────────────────────────────────────
    public List<BidDAO.BidRecord> getBidHistory(String productId) {
        return bidDAO.getBidRecords(productId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET ACTIVE AUCTIONS
    // ─────────────────────────────────────────────────────────────────────
    public List<Item> getActiveAuctions() {
        List<Map<String, Object>> rows = auctionDAO.findAllOpen();
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Item item = mapToItem(row);
            if (item != null) items.add(item);
        }
        return items;
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET PRODUCTS BY SELLER
    // ─────────────────────────────────────────────────────────────────────
    public List<Item> getProductsBySeller(String sellerId) {
        List<Map<String, Object>> rows = auctionDAO.findBySeller(sellerId);
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Item item = mapToItem(row);
            if (item != null) items.add(item);
        }
        return items;
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADD PRODUCT
    // ─────────────────────────────────────────────────────────────────────
    public Item addProduct(String sellerId, String name, String description,
                           double startPrice, double bidIncrement,
                           String imagePath, LocalDateTime startTime,
                           LocalDateTime endTime) {
        String itemId = "I-" + UUID.randomUUID().toString().substring(0, 8);

        Art item = new Art(itemId, name, startPrice, endTime, sellerId, description);
        item.setBidIncrement(bidIncrement);
        item.setImagePath(imagePath);
        item.setStartTime(startTime);
        item.setStatus("OPEN");
        item.setDescription(description);

        boolean itemSaved = itemSaveDAO.saveItem(item);
        if (!itemSaved) {
            System.err.println("[AuctionService] saveItem that bai: " + name);
            return null;
        }

        long auctionId = System.currentTimeMillis();
        boolean auctionSaved = auctionDAO.saveAuction(
                auctionId, itemId, sellerId, startPrice, endTime.toString());
        if (!auctionSaved) {
            System.err.println("[AuctionService] saveAuction that bai: " + itemId);
            return null;
        }

        System.out.printf("[AuctionService] Them san pham OK: id=%s name=%s price=%.0f%n",
                itemId, name, startPrice);
        return item;
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE PRODUCT
    // ─────────────────────────────────────────────────────────────────────
    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime) {
        return itemSaveDAO.updateItem(productId, name, description, startPrice, bidIncrement, endTime);
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE PRODUCT
    // ─────────────────────────────────────────────────────────────────────
    public boolean deleteProduct(String productId) {
        return itemSaveDAO.deleteItem(productId);
    }

    // ─────────────────────────────────────────────────────────────────────
    // END AUCTION (FIX: tra ve Map co "itemId" de controller broadcast duoc)
    // ─────────────────────────────────────────────────────────────────────
    public Map<String, Object> endAuction(long auctionId) {
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return null;

        String status = (String) auction.get("status");
        if ("FINISHED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status))
            return null;

        Map<String, Object> winner = auctionDAO.findWinner(auctionId);

        String itemId = (String) auction.get("itemId");
        if (itemId == null) itemId = (String) auction.get("item_id");

        if (winner != null) {
            String winnerId   = (String) winner.get("bidderId");
            double finalPrice = ((Number) winner.get("finalPrice")).doubleValue();

            transactionDAO.saveTransaction(itemId, winnerId, finalPrice);
            auctionDAO.updateStatus(auctionId, "FINISHED");

            winner.put("itemId", itemId);
            return winner;
        } else {
            auctionDAO.updateStatus(auctionId, "CANCELED");
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper: Map DB row -> Item
    // ─────────────────────────────────────────────────────────────────────
    private Item mapToItem(Map<String, Object> row) {
        try {
            String itemId   = (String) row.get("itemId");
            if (itemId == null) itemId = (String) row.get("item_id");
            String itemName = (String) row.get("itemName");
            if (itemName == null) itemName = (String) row.get("item_name");
            String sellerId = (String) row.get("sellerId");
            if (sellerId == null) sellerId = (String) row.get("seller_id");
            String status   = (String) row.get("status");

            String endTimeStr = (String) row.get("endTime");
            if (endTimeStr == null) endTimeStr = (String) row.get("end_time");

            LocalDateTime endTime = null;
            if (endTimeStr != null) {
                try { endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T")); }
                catch (Exception ignored) {}
            }

            double currentPrice  = toDouble(row.getOrDefault("currentPrice",
                                   row.getOrDefault("current_price", 0.0)));
            double startingPrice = toDouble(row.getOrDefault("startingPrice",
                                   row.getOrDefault("starting_price", currentPrice)));
            if (startingPrice == 0) startingPrice = currentPrice;

            double bidIncrement = toDouble(row.getOrDefault("bidIncrement",
                                  row.getOrDefault("bid_increment", 0.0)));

            String description = (String) row.getOrDefault("description", "");
            if (description == null) description = "";
            String sellerName  = (String) row.getOrDefault("sellerName",
                                 row.getOrDefault("seller_name", null));
            String imagePath   = (String) row.getOrDefault("imagePath",
                                 row.getOrDefault("image_path", ""));

            String type = (String) row.getOrDefault("type", "ART");
            Item item = switch (type.toUpperCase()) {
                case "ELECTRONICS" -> new Electronics(itemId, itemName, startingPrice, endTime, sellerId, 0);
                case "VEHICLE"     -> new Vehicle(itemId, itemName, startingPrice, endTime, sellerId, 0);
                default            -> new Art(itemId, itemName, startingPrice, endTime, sellerId, description);
            };

            item.setCurrentBid(currentPrice);
            item.setStatus(status != null ? status : "OPEN");
            item.setBidIncrement(bidIncrement);
            if (sellerName != null) item.setSellerName(sellerName);
            if (imagePath  != null && !imagePath.isBlank()) item.setImagePath(imagePath);
            if (!description.isBlank()) item.setDescription(description);

            return item;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private double toDouble(Object obj) {
        if (obj instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Inner types
    // ─────────────────────────────────────────────────────────────────────
    public enum BidResult {
        SUCCESS, PRICE_TOO_LOW, AUCTION_ENDED, AUCTION_NOT_FOUND, ERROR
    }

    public record BidOutcome(
            BidResult result,
            double    newBid,
            String    message,
            String    newEndTime
    ) {}

    /**
     * Lấy lịch sử đấu giá của 1 bidder.
     * Trả về list các phiên bidder đã tham gia kèm:
     *   - itemName, myBid (giá cao nhất bidder đã đặt)
     *   - finalPrice, winnerId (từ bảng auctions/transactions)
     *   - status, endTime, sellerName
     */
    public List<Map<String, Object>> getBidHistoryForBidder(String bidderId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT i.id AS itemId, i.name AS itemName, MAX(b.bid_price) AS myBid, " +
                "a.current_price AS finalPrice, a.status, a.end_time AS endTime, " +
                "u.username AS sellerName, t.winner_id AS winnerId " +
                "FROM bids b " +
                "JOIN items i      ON b.item_id   = i.id " +
                "JOIN auctions a   ON a.item_id   = i.id " +
                "LEFT JOIN users u ON i.seller_id = u.id " +
                "LEFT JOIN transactions t ON t.item_id = i.id " +
                "WHERE b.bidder_id = ? " +
                "GROUP BY i.id, i.name, a.current_price, a.status, a.end_time, u.username, t.winner_id";
        try (Connection conn = com.auction.server.database.DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(Map.of(
                            "itemId", rs.getString("itemId"),
                            "itemName", rs.getString("itemName"),
                            "status", rs.getString("status")
                    ));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // Sửa nhận thông tin vào Bid History
    public List<java.util.Map<String, Object>> getUserBidHistory(String bidderId) {
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();

        // Câu lệnh SQL chuẩn hóa theo đúng cấu trúc bảng hệ thống của bạn
        String sql = "SELECT " +
                "  i.id             AS itemId, " +
                "  i.name           AS itemName, " +
                "  MAX(b.bid_price) AS myBid, " +
                "  a.current_price  AS finalPrice, " +
                "  a.status         AS status, " +
                "  a.end_time       AS endTime, " +
                "  u.username       AS sellerName, " +
                "  t.winner_id      AS winnerId " +
                "FROM bids b " +
                "JOIN items i        ON b.item_id   = i.id " +
                "JOIN auctions a     ON a.item_id   = i.id " +
                "LEFT JOIN users u   ON i.seller_id = u.id " +
                "LEFT JOIN transactions t ON t.item_id = i.id " +
                "WHERE b.bidder_id = ? " +
                "GROUP BY i.id, i.name, a.current_price, a.status, a.end_time, u.username, t.winner_id";

        try {
            java.sql.Connection conn = com.auction.server.database.DatabaseConnection.getConnection();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

                // Thử nghiệm parse về Long nếu ID hệ thống của bạn dùng số tự tăng,
                // nếu ID dạng chuỗi text thì giữ nguyên ps.setString(1, bidderId);
                try {
                    ps.setLong(1, Long.parseLong(bidderId));
                } catch (NumberFormatException e) {
                    ps.setString(1, bidderId);
                }

                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        java.util.Map<String, Object> row = new java.util.HashMap<>();
                        row.put("itemId",     rs.getString("itemId"));
                        row.put("itemName",   rs.getString("itemName"));
                        row.put("myBid",      rs.getDouble("myBid"));
                        row.put("finalPrice", rs.getDouble("finalPrice"));

                        String wId = rs.getString("winnerId");
                        row.put("winnerId",   wId != null ? wId : "");

                        String stat = rs.getString("status");
                        row.put("status",     stat != null ? stat : "FINISHED");

                        String eTime = rs.getString("endTime");
                        row.put("endTime",    eTime != null ? eTime : "");

                        String sName = rs.getString("sellerName");
                        row.put("sellerName", sName != null ? sName : "Không rõ");

                        list.add(row);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[AuctionService] Lỗi quét lịch sử đồng bộ hệ thống: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}