package com.auction.server.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.item.ItemSaveDAO;
import com.auction.server.dao.transaction.TransactionDAO;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Item;

public class AuctionService {

    private static final int SNIPE_WINDOW_SECONDS = 60;
    private static final int SNIPE_EXTEND_SECONDS = 60;

    private final AuctionDAO     auctionDAO     = new AuctionDAO();
    private final BidDAO         bidDAO         = new BidDAO();
    private final ItemSaveDAO    itemSaveDAO    = new ItemSaveDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    private final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private ReentrantLock getLock(String productId) {
        return lockMap.computeIfAbsent(productId, id -> new ReentrantLock());
    }

    // ═══════════════════════════════════════════════════════════
    // PLACE BID
    // ═══════════════════════════════════════════════════════════
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
                return new BidOutcome(BidResult.AUCTION_NOT_FOUND, 0, "Không tìm thấy phiên đấu giá.", null);

            String status = (String) targetAuction.get("status");
            if (!"OPEN".equals(status) && !"RUNNING".equals(status))
                return new BidOutcome(BidResult.AUCTION_ENDED, 0, "Phiên đấu giá đã kết thúc.", null);

            String endTimeStr = (String) targetAuction.get("endTime");
            LocalDateTime endTime = null;
            if (endTimeStr != null) {
                endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T"));
                if (LocalDateTime.now().isAfter(endTime))
                    return new BidOutcome(BidResult.AUCTION_ENDED, 0, "Phiên đấu giá đã hết giờ.", null);
            }

            double currentPrice = (double) targetAuction.get("currentPrice");
            if (amount <= currentPrice)
                return new BidOutcome(BidResult.PRICE_TOO_LOW, currentPrice,
                        String.format("Giá phải cao hơn %.0f VNĐ.", currentPrice), null);

            long auctionId = (long) targetAuction.get("id");
            boolean bidSaved = bidDAO.placeBid(productId, bidderId, amount);
            if (!bidSaved)
                return new BidOutcome(BidResult.ERROR, currentPrice, "Lỗi lưu bid.", null);

            auctionDAO.updateCurrentPrice(auctionId, amount);

            String newEndTimeStr = null;
            if (endTime != null) {
                long secondsLeft = java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();
                if (secondsLeft <= SNIPE_WINDOW_SECONDS) {
                    boolean extended = auctionDAO.extendEndTime(auctionId, SNIPE_EXTEND_SECONDS);
                    if (extended) {
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

    // ═══════════════════════════════════════════════════════════
    // GET BID HISTORY
    // ═══════════════════════════════════════════════════════════
    public List<BidDAO.BidRecord> getBidHistory(String productId) {
        return bidDAO.getBidRecords(productId);
    }

    // ═══════════════════════════════════════════════════════════
    // GET ACTIVE AUCTIONS
    // ═══════════════════════════════════════════════════════════
    public List<Item> getActiveAuctions() {
        List<Map<String, Object>> rows = auctionDAO.findAllOpen();
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(mapToItem(row));
        }
        return items;
    }

    // ═══════════════════════════════════════════════════════════
    // GET PRODUCTS BY SELLER — trả tất cả sp của seller (mọi status)
    // ═══════════════════════════════════════════════════════════
    public List<Item> getProductsBySeller(String sellerId) {
        List<Map<String, Object>> rows = auctionDAO.findBySeller(sellerId);
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            items.add(mapToItem(row));
        }
        return items;
    }

    // ═══════════════════════════════════════════════════════════
    // ADD PRODUCT
    // ═══════════════════════════════════════════════════════════
    public Item addProduct(String sellerId, String name, String description,
                           double startPrice, double bidIncrement,
                           String imagePath, LocalDateTime startTime,
                           LocalDateTime endTime) {
        String itemId = "I-" + UUID.randomUUID().toString().substring(0, 8);

        Art item = new Art(itemId, name, startPrice, endTime, sellerId, description);
        item.setBidIncrement(bidIncrement);
        item.setImagePath(imagePath);
        item.setStartTime(startTime);
        item.setStatus("OPEN");   // FIX: PENDING → OPEN để hiện ngay trong bảng
        item.setDescription(description);

        boolean itemSaved = itemSaveDAO.saveItem(item);
        if (!itemSaved) {
            System.err.println("[AuctionService] saveItem thất bại cho: " + name);
            return null;
        }

        long auctionId = System.currentTimeMillis();
        boolean auctionSaved = auctionDAO.saveAuction(
                auctionId, itemId, sellerId, startPrice, endTime.toString());
        if (!auctionSaved) {
            System.err.println("[AuctionService] saveAuction thất bại cho itemId: " + itemId);
            return null;
        }

        System.out.printf("[AuctionService] Thêm sản phẩm OK: id=%s name=%s price=%.0f%n",
                itemId, name, startPrice);
        return item;
    }

    // ═══════════════════════════════════════════════════════════
    // UPDATE PRODUCT
    // ═══════════════════════════════════════════════════════════
    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime) {
        String sql = "UPDATE items SET name=?, current_price=?, end_time=?, description=?, bid_increment=? WHERE id=?";
        try (java.sql.Connection conn =
                     com.auction.server.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, startPrice);
            ps.setString(3, endTime.toString());
            ps.setString(4, description != null ? description : "");
            ps.setDouble(5, bidIncrement);
            ps.setString(6, productId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            // Fallback không có description/bid_increment columns
            String sqlFallback = "UPDATE items SET name=?, current_price=?, end_time=? WHERE id=?";
            try (java.sql.Connection conn =
                         com.auction.server.database.DatabaseConnection.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sqlFallback)) {
                ps.setString(1, name);
                ps.setDouble(2, startPrice);
                ps.setString(3, endTime.toString());
                ps.setString(4, productId);
                return ps.executeUpdate() > 0;
            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE PRODUCT
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    // END AUCTION
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    // HELPER: Map DB row → Item
    // ═══════════════════════════════════════════════════════════
    private Item mapToItem(Map<String, Object> row) {
        String itemId   = (String) row.get("itemId");
        String itemName = (String) row.get("itemName");
        String sellerId = (String) row.get("sellerId");
        String status   = (String) row.get("status");
        String endTimeStr = (String) row.get("endTime");

        double currentPrice = 0;
        Object price = row.get("currentPrice");
        if (price instanceof Double d)      currentPrice = d;
        else if (price instanceof Number n) currentPrice = n.doubleValue();

        double startingPrice = currentPrice;
        Object sp = row.get("startingPrice");
        if (sp instanceof Double d)      startingPrice = d;
        else if (sp instanceof Number n) startingPrice = n.doubleValue();

        LocalDateTime endTime = null;
        if (endTimeStr != null) {
            try { endTime = LocalDateTime.parse(endTimeStr.replace(" ", "T")); }
            catch (Exception ignored) {}
        }

        String description = "";
        if (row.containsKey("description") && row.get("description") != null)
            description = (String) row.get("description");

        String sellerName = null;
        if (row.containsKey("sellerName") && row.get("sellerName") != null)
            sellerName = (String) row.get("sellerName");

        double bidIncrement = 0;
        if (row.containsKey("bidIncrement") && row.get("bidIncrement") != null) {
            Object bi = row.get("bidIncrement");
            if (bi instanceof Number n) bidIncrement = n.doubleValue();
        }

        String imagePath = null;
        if (row.containsKey("imagePath") && row.get("imagePath") != null)
            imagePath = (String) row.get("imagePath");

        Art item = new Art(itemId, itemName, startingPrice, endTime, sellerId, description);
        item.setCurrentBid(currentPrice);
        item.setStatus(status != null ? status : "OPEN");
        item.setBidIncrement(bidIncrement);
        if (sellerName != null) item.setSellerName(sellerName);
        if (imagePath != null && !imagePath.isBlank()) item.setImagePath(imagePath);

        return item;
    }

    // ═══════════════════════════════════════════════════════════
    // INNER TYPES
    // ═══════════════════════════════════════════════════════════
    public enum BidResult {
        SUCCESS, PRICE_TOO_LOW, AUCTION_ENDED, AUCTION_NOT_FOUND, ERROR
    }

    public record BidOutcome(
            BidResult result,
            double    newBid,
            String    message,
            String    newEndTime
    ) {}
}