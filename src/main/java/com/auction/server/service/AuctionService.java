package com.auction.server.service;

import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.item.ItemSaveDAO;
import com.auction.server.dao.transaction.TransactionDAO;
import com.auction.server.database.DatabaseConnection;
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

public class AuctionService {

    private static final int SNIPE_WINDOW_SECONDS = 60;
    private static final int SNIPE_EXTEND_SECONDS = 60;

    private final AuctionDAO     auctionDAO     = new AuctionDAO();
    private final BidDAO         bidDAO         = new BidDAO();
    private final ItemSaveDAO    itemSaveDAO    = new ItemSaveDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    // ── STATIC: tất cả instance dùng chung 1 lockMap ─────────────────────
    private static final ConcurrentHashMap<String, ReentrantLock> lockMap =
            new ConcurrentHashMap<>();

    private static ReentrantLock getLock(String productId) {
        return lockMap.computeIfAbsent(productId, id -> new ReentrantLock(true));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  placeBid — toàn bộ logic đặt giá nằm trong 1 DB transaction duy nhất
    //
    //  Fix so với phiên bản trước:
    //  1. Dùng autoCommit=false + SELECT FOR UPDATE trong CÙNG 1 Connection
    //     → FOR UPDATE chỉ lock được khi nằm trong explicit transaction
    //  2. Tất cả INSERT/UPDATE (bid, auction price, item price) dùng cùng
    //     Connection đó → nếu bất kỳ bước nào lỗi thì rollback toàn bộ
    //  3. currentPrice đọc từ auctions.current_price (nguồn duy nhất đúng),
    //     KHÔNG đọc từ items.current_price (có thể lệch)
    // ─────────────────────────────────────────────────────────────────────
    public BidOutcome placeBid(String productId, String bidderId, double amount) {
        ReentrantLock lock = getLock(productId);
        lock.lock();
        try {
            return placeBidInTransaction(productId, bidderId, amount);
        } finally {
            lock.unlock();
        }
    }

    private BidOutcome placeBidInTransaction(String productId, String bidderId, double amount) {
        // Mở 1 Connection duy nhất, tắt autoCommit để bắt đầu explicit transaction
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // ── Bước 1: Đọc auction với FOR UPDATE (lock row DB) ──────
                // FOR UPDATE chỉ có hiệu lực khi autoCommit=false
                // → thread khác SELECT cùng row phải chờ transaction này commit
                BidCheckResult check = readForUpdate(conn, productId);

                if (check == null) {
                    conn.rollback();
                    return new BidOutcome(BidResult.AUCTION_NOT_FOUND, 0,
                            "Khong tim thay phien dau gia.", null);
                }

                if (!"OPEN".equals(check.status) && !"RUNNING".equals(check.status)) {
                    conn.rollback();
                    return new BidOutcome(BidResult.AUCTION_ENDED, 0,
                            "Phien dau gia da ket thuc.", null);
                }

                if (check.endTime != null && LocalDateTime.now().isAfter(check.endTime)) {
                    conn.rollback();
                    return new BidOutcome(BidResult.AUCTION_ENDED, 0,
                            "Phien dau gia da het gio.", null);
                }

                if (bidderId.equals(check.sellerId)) {
                    conn.rollback();
                    return new BidOutcome(BidResult.ERROR, 0,
                            "Seller khong the tu dat gia vao phien cua minh.", null);
                }

                // ── Bước 2: Kiểm tra giá — dùng currentPrice từ FOR UPDATE ─
                // Đây là giá MỚI NHẤT, đã bị lock, không thể bị thread khác
                // thay đổi cho đến khi transaction này commit/rollback
                if (amount <= check.currentPrice) {
                    conn.rollback();
                    return new BidOutcome(BidResult.PRICE_TOO_LOW, check.currentPrice,
                            String.format("Gia phai cao hon %.0f VND.", check.currentPrice), null);
                }

                // ── Bước 3: Lưu bid ──────────────────────────────────────
                boolean bidSaved = insertBid(conn, productId, bidderId, amount);
                if (!bidSaved) {
                    conn.rollback();
                    return new BidOutcome(BidResult.ERROR, check.currentPrice,
                            "Loi luu bid.", null);
                }

                // ── Bước 4: Cập nhật giá trong auctions VÀ items ─────────
                updateAuctionPrice(conn, check.auctionId, amount);
                updateItemPrice(conn, productId, amount);

                // ── Bước 5: Anti-sniping ──────────────────────────────────
                String newEndTimeStr = null;
                if (check.endTime != null) {
                    long secondsLeft = java.time.Duration
                            .between(LocalDateTime.now(), check.endTime).getSeconds();
                    if (secondsLeft <= SNIPE_WINDOW_SECONDS) {
                        extendEndTime(conn, check.auctionId, SNIPE_EXTEND_SECONDS);
                        newEndTimeStr = getEndTime(conn, check.auctionId);
                        System.out.printf("[AuctionService] Anti-sniping: phien #%d gia han +%ds%n",
                                check.auctionId, SNIPE_EXTEND_SECONDS);
                    }
                }

                // ── Commit toàn bộ ────────────────────────────────────────
                conn.commit();
                System.out.printf("[AuctionService] BID OK: product=%s bidder=%s amount=%.0f%n",
                        productId, bidderId, amount);

                return new BidOutcome(BidResult.SUCCESS, amount,
                        "Dat gia thanh cong!", newEndTimeStr);

            } catch (Exception e) {
                conn.rollback();
                System.err.println("[AuctionService] placeBid rollback: " + e.getMessage());
                e.printStackTrace();
                return new BidOutcome(BidResult.ERROR, 0,
                        "Loi he thong: " + e.getMessage(), null);
            }
        } catch (Exception e) {
            System.err.println("[AuctionService] Loi mo connection: " + e.getMessage());
            return new BidOutcome(BidResult.ERROR, 0,
                    "Loi ket noi database.", null);
        }
    }

    // ── SQL helpers — tất cả dùng Connection được truyền vào ─────────────

    /** SELECT FOR UPDATE: lock row auction, trả về thông tin cần thiết. */
    private BidCheckResult readForUpdate(Connection conn, String productId) throws Exception {
        String sql = "SELECT a.id, a.seller_id, a.status, a.end_time, a.current_price " +
                     "FROM auctions a " +
                     "WHERE a.item_id = ? AND a.status IN ('OPEN','RUNNING') " +
                     "ORDER BY a.id DESC LIMIT 1 " +
                     "FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long          auctionId    = rs.getLong("id");
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

    private boolean insertBid(Connection conn, String itemId,
                               String bidderId, double amount) throws Exception {
        String sql = "INSERT INTO bids (item_id, bidder_id, bid_price, bid_time) " +
                     "VALUES (?, ?, ?, NOW())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ps.setString(2, bidderId);
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        }
    }

    private void updateAuctionPrice(Connection conn, long auctionId,
                                    double newPrice) throws Exception {
        String sql = "UPDATE auctions SET current_price = ?, status = 'RUNNING' WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setLong(2, auctionId);
            ps.executeUpdate();
        }
    }

    private void updateItemPrice(Connection conn, String itemId,
                                 double newPrice) throws Exception {
        String sql = "UPDATE items SET current_price = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setString(2, itemId);
            ps.executeUpdate();
        }
    }

    private void extendEndTime(Connection conn, long auctionId,
                               int extraSeconds) throws Exception {
        String sql = "UPDATE auctions " +
                     "SET end_time = DATE_ADD(end_time, INTERVAL ? SECOND) WHERE id = ?";
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

    /** Kết quả đọc auction để kiểm tra trước khi đặt giá. */
    private record BidCheckResult(
            long          auctionId,
            String        sellerId,
            String        status,
            double        currentPrice,
            LocalDateTime endTime
    ) {}

    // ─────────────────────────────────────────────────────────────────────
    // Các method còn lại — giữ nguyên
    // ─────────────────────────────────────────────────────────────────────

    public List<BidDAO.BidRecord> getBidHistory(String productId) {
        return bidDAO.getBidRecords(productId);
    }

    public List<Item> getActiveAuctions() {
        List<Map<String, Object>> rows = auctionDAO.findAllOpen();
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Item item = mapToItem(row);
            if (item != null) items.add(item);
        }
        return items;
    }

    public List<Item> getProductsBySeller(String sellerId) {
        List<Map<String, Object>> rows = auctionDAO.findBySeller(sellerId);
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Item item = mapToItem(row);
            if (item != null) items.add(item);
        }
        return items;
    }

    public Item addProduct(String sellerId, String name, String description,
                           double startPrice, double bidIncrement,
                           String imagePath, LocalDateTime startTime,
                           LocalDateTime endTime, String type) {

        String itemId   = "I-" + UUID.randomUUID().toString().substring(0, 8);
        String itemType = (type != null && !type.isBlank()) ? type.toUpperCase() : "ART";

        Item item = switch (itemType) {
            case "ELECTRONICS" -> {
                Electronics e = new Electronics(itemId, name, startPrice, endTime, sellerId, 0);
                e.setDescription(description);
                yield e;
            }
            case "VEHICLE" -> {
                Vehicle v = new Vehicle(itemId, name, startPrice, endTime, sellerId, 0);
                v.setDescription(description);
                yield v;
            }
            default -> new Art(itemId, name, startPrice, endTime, sellerId, description);
        };

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

        System.out.printf("[AuctionService] Them san pham OK: id=%s name=%s type=%s price=%.0f%n",
                itemId, name, itemType, startPrice);
        return item;
    }

    public Item addProduct(String sellerId, String name, String description,
                           double startPrice, double bidIncrement,
                           String imagePath, LocalDateTime startTime,
                           LocalDateTime endTime) {
        return addProduct(sellerId, name, description, startPrice, bidIncrement,
                imagePath, startTime, endTime, "ART");
    }

    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime, String imagePath, String type) {
        if (imagePath != null && !imagePath.isBlank()) {
            return itemSaveDAO.updateItem(productId, name, description,
                    startPrice, bidIncrement, endTime, imagePath, type);
        } else {
            return itemSaveDAO.updateItem(productId, name, description,
                    startPrice, bidIncrement, endTime, type);
        }
    }

    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime, String type) {
        return itemSaveDAO.updateItem(productId, name, description,
                startPrice, bidIncrement, endTime, type);
    }

    public boolean deleteProduct(String productId) {
        return itemSaveDAO.deleteItem(productId);
    }

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

            String typeFromDb = (String) row.getOrDefault("type", "ART");
            if (typeFromDb == null) typeFromDb = "ART";
            Item item = switch (typeFromDb.toUpperCase()) {
                case "ELECTRONICS" -> new Electronics(itemId, itemName, startingPrice, endTime, sellerId, 0);
                case "VEHICLE"     -> new Vehicle(itemId, itemName, startingPrice, endTime, sellerId, 0);
                default            -> new Art(itemId, itemName, startingPrice, endTime, sellerId, description);
            };

            item.setType(typeFromDb.toUpperCase());
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

    public enum BidResult {
        SUCCESS, PRICE_TOO_LOW, AUCTION_ENDED, AUCTION_NOT_FOUND, ERROR
    }

    public record BidOutcome(
            BidResult result,
            double    newBid,
            String    message,
            String    newEndTime
    ) {}

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
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bidderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(Map.of(
                            "itemId",   rs.getString("itemId"),
                            "itemName", rs.getString("itemName"),
                            "status",   rs.getString("status")
                    ));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    public List<java.util.Map<String, Object>> getUserBidHistory(String bidderId) {
        List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
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
            java.sql.Connection conn = DatabaseConnection.getConnection();
            try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
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
            System.err.println("[AuctionService] Lỗi getUserBidHistory: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }
}
