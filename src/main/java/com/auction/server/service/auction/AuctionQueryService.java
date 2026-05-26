package com.auction.server.service.auction;

import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AuctionQueryService - Quản lý tìm kiếm, truy vấn dữ liệu từ DB và Mapping lên UI.
 */
public class AuctionQueryService {

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final BidDAO     bidDAO     = new BidDAO();

    /**
     * SỬA ĐỔI CHÍ MẠNG: Đổi kiểu trả về thành List<Map> để tránh lỗi không tìm thấy class 'BidRecord' nội bộ của BidDAO.
     */
    public List<Map<String, Object>> getBidHistory(String productId) {
        String sql = "SELECT b.id, b.item_id, b.bidder_id, b.bid_price, b.bid_time, u.username " +
                     "FROM bids b LEFT JOIN users u ON b.bidder_id = u.id " +
                     "WHERE b.item_id = ? ORDER BY b.bid_price DESC";
        List<Map<String, Object>> history = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.add(Map.of(
                        "id", rs.getLong("id"),
                        "itemId", rs.getString("item_id"),
                        "bidderId", rs.getString("bidder_id"),
                        "username", rs.getString("username") != null ? rs.getString("username") : "Khách",
                        "bidPrice", rs.getDouble("bid_price"),
                        "bidTime", rs.getString("bid_time") != null ? rs.getString("bid_time") : ""
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("[❌ Lỗi Query History]: " + e.getMessage());
        }
        return history;
    }

    /**
     * Lấy toàn bộ danh sách phòng đấu giá đang diễn ra (ACTIVE)
     */
    public List<Item> getActiveAuctions() {
        List<Map<String, Object>> rows = auctionDAO.findAllOpen();
        List<Item> items = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map<String, Object> row : rows) {
            String endTimeStr = (String) row.get("endTime");
            if (endTimeStr == null) endTimeStr = (String) row.get("end_time");
            if (endTimeStr != null) {
                try {
                    LocalDateTime endTime = LocalDateTime.parse(
                            endTimeStr.replace(" ", "T").substring(0, 19));
                    if (now.isAfter(endTime)) continue; 
                } catch (Exception ignored) {}
            }
            
            Item item = mapToItem(row);
            if (item != null) items.add(item);
        }
        return items;
    }

    /**
     * Truy vấn toàn bộ sản phẩm do một người bán (Seller) cụ thể đăng tải
     */
    public List<Item> getProductsBySeller(String sellerId) {
        List<Map<String, Object>> rows = auctionDAO.findBySeller(sellerId);
        List<Item> items = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Item item = mapToItem(row);
            if (item != null) items.add(item);
        }
        return items;
    }

    /**
     * Trích xuất lịch sử tổng hợp các phòng đấu giá mà cá nhân tài khoản người dùng đã từng đặt tiền cược vào
     */
    public List<Map<String, Object>> getUserBidHistory(String bidderId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT i.id AS itemId, i.name AS itemName, MAX(b.bid_price) AS myBid, " +
                "a.current_price AS finalPrice, a.status, a.end_time AS endTime, " +
                "u.username AS sellerName, t.winner_id AS winnerId " +
                "FROM bids b " +
                "JOIN items i        ON b.item_id   = i.id " +
                "JOIN auctions a     ON a.item_id   = i.id " +
                "LEFT JOIN users u   ON i.seller_id = u.id " +
                "LEFT JOIN transactions t ON t.item_id = i.id " +
                "WHERE b.bidder_id = ? " +
                "GROUP BY i.id, i.name, a.current_price, a.status, a.end_time, u.username, t.winner_id";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            try {
                ps.setLong(1, Long.parseLong(bidderId));
            } catch (NumberFormatException e) {
                ps.setString(1, bidderId);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
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
        } catch (Exception e) {
            System.err.println("[AuctionQueryService] Lỗi getUserBidHistory: " + e.getMessage());
        }
        return list;
    }

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

    private Item mapToItem(Map<String, Object> row) {
        try {
            String itemId = (String) row.get("itemId");
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

            double currentPrice  = toDouble(row.getOrDefault("currentPrice", row.getOrDefault("current_price", 0.0)));
            double startingPrice = toDouble(row.getOrDefault("startingPrice", row.getOrDefault("starting_price", currentPrice)));
            if (startingPrice == 0) startingPrice = currentPrice;

            double bidIncrement = toDouble(row.getOrDefault("bidIncrement", row.getOrDefault("bid_increment", 0.0)));

            String description = (String) row.getOrDefault("description", "");
            if (description == null) description = "";
            String sellerName  = (String) row.getOrDefault("sellerName", row.getOrDefault("seller_name", null));
            String imagePath   = (String) row.getOrDefault("imagePath", row.getOrDefault("image_path", ""));

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
}