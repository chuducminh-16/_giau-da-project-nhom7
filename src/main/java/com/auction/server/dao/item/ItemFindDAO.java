package com.auction.server.dao.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;

public class ItemFindDAO {

    public Item findById(String itemId) {
        String sql = "SELECT i.*, u.username as seller_name " +
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

    static Item mapRow(ResultSet rs) throws Exception {
        String type     = rs.getString("type");
        String id       = rs.getString("id");
        String name     = rs.getString("name");
        String sellerId = rs.getString("seller_id");

        String endTimeStr = rs.getString("end_time");
        LocalDateTime endTime = (endTimeStr != null)
                ? LocalDateTime.parse(endTimeStr.replace(" ", "T")) : null;

        // current_price
        double currentPrice = rs.getDouble("current_price");

        // starting_price — fallback về current_price nếu cột chưa có
        double startingPrice = currentPrice;
        try { startingPrice = rs.getDouble("starting_price"); } catch (Exception ignored) {}
        if (startingPrice == 0) startingPrice = currentPrice;

        // các cột mở rộng
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
        if (imagePath != null && !imagePath.isBlank()) item.setImagePath(imagePath);
        if (sellerName != null) item.setSellerName(sellerName);
        if (!description.isBlank()) item.setDescription(description);

        return item;
    }
}