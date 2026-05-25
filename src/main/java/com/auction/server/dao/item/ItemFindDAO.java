package com.auction.server.dao.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;

public class ItemFindDAO {

    // ── Tìm 1 item theo id ────────────────────────────────────────────────
    public Item findById(String itemId) {
        String sql = "SELECT i.*, u.username AS seller_name " +
                     "FROM items i LEFT JOIN users u ON i.seller_id = u.id " +
                     "WHERE i.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, itemId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // ── FIX 1: Thêm findBySeller — AuctionService.getProductsBySeller() cần ──
    public List<Item> findBySeller(String sellerId) {
        List<Item> list = new ArrayList<>();
        String sql = "SELECT i.*, u.username AS seller_name " +
                     "FROM items i LEFT JOIN users u ON i.seller_id = u.id " +
                     "WHERE i.seller_id = ? " +
                     "ORDER BY i.end_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Item item = mapRow(rs);
                if (item != null) list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ── FIX 2: Thêm findAllOpen — AuctionService.getActiveAuctions() cần ──
    public List<Item> findAllOpen() {
        List<Item> list = new ArrayList<>();
        String sql = "SELECT i.*, u.username AS seller_name " +
                     "FROM items i LEFT JOIN users u ON i.seller_id = u.id " +
                     "WHERE i.status IN ('OPEN','RUNNING') AND i.end_time > NOW() " +
                     "ORDER BY i.end_time ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Item item = mapRow(rs);
                if (item != null) list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    // ── mapRow — giữ nguyên logic cũ, chỉ thêm startTime ────────────────
    static Item mapRow(ResultSet rs) throws Exception {
        String type     = rs.getString("type");
        String id       = rs.getString("id");
        String name     = rs.getString("name");
        String sellerId = rs.getString("seller_id");

        String endTimeStr = rs.getString("end_time");
        LocalDateTime endTime = (endTimeStr != null)
                ? LocalDateTime.parse(endTimeStr.replace(" ", "T")) : null;

        double currentPrice = rs.getDouble("current_price");

        double startingPrice = currentPrice;
        try { startingPrice = rs.getDouble("starting_price"); } catch (Exception ignored) {}
        if (startingPrice == 0) startingPrice = currentPrice;

        String description = "";
        try { description = rs.getString("description"); } catch (Exception ignored) {}
        if (description == null) description = "";

        double bidIncrement = 0;
        try { bidIncrement = rs.getDouble("bid_increment"); } catch (Exception ignored) {}

        String imagePath = "";
        try { imagePath = rs.getString("image_path"); } catch (Exception ignored) {}

        String status = "OPEN";
        try { status = rs.getString("status"); } catch (Exception ignored) {}

        String sellerName = null;
        try { sellerName = rs.getString("seller_name"); } catch (Exception ignored) {}

        // FIX 3: Đọc startTime — ManageProductController cần để hiển thị form
        LocalDateTime startTime = null;
        try {
            String startTimeStr = rs.getString("start_time");
            if (startTimeStr != null)
                startTime = LocalDateTime.parse(startTimeStr.replace(" ", "T"));
        } catch (Exception ignored) {}

        // Tạo đúng subclass theo type
        Item item = switch (type != null ? type.toUpperCase() : "ART") {
            case "ELECTRONICS" -> {
                int warranty = 0;
                try { warranty = Integer.parseInt(description); } catch (Exception ignored) {}
                yield new Electronics(id, name, startingPrice, endTime, sellerId, warranty);
            }
            case "VEHICLE" -> {
                int mileage = 0;
                try { mileage = Integer.parseInt(description); } catch (Exception ignored) {}
                yield new Vehicle(id, name, startingPrice, endTime, sellerId, mileage);
            }
            default -> new Art(id, name, startingPrice, endTime, sellerId, description);
        };

        item.setCurrentBid(currentPrice);
        item.setBidIncrement(bidIncrement);
        item.setStatus(status != null ? status : "OPEN");
        if (imagePath   != null && !imagePath.isBlank())   item.setImagePath(imagePath);
        if (sellerName  != null)                           item.setSellerName(sellerName);
        if (!description.isBlank())                        item.setDescription(description);
        if (startTime   != null)                           item.setStartTime(startTime);  // FIX 3

        return item;
    }
}
