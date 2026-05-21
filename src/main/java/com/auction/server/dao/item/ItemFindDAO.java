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
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return mapRow(rs);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static Item mapRow(ResultSet rs) throws Exception {
        String type     = rs.getString("type");
        String id       = rs.getString("id");
        String name     = rs.getString("name");
        double price    = rs.getDouble("current_price");
        String sellerId = rs.getString("seller_id");

        String endTimeStr = rs.getString("end_time");
        LocalDateTime endTime = (endTimeStr != null)
                ? LocalDateTime.parse(endTimeStr.replace(" ", "T")) : null;

        // Lấy description từ DB nếu cột tồn tại
        String description = "";
        try { description = rs.getString("description"); } catch (Exception ignored) {}
        if (description == null) description = "";

        // Lấy bid_increment nếu có
        double bidIncrement = 0;
        try { bidIncrement = rs.getDouble("bid_increment"); } catch (Exception ignored) {}

        // Lấy image_path nếu có
        String imagePath = "";
        try { imagePath = rs.getString("image_path"); } catch (Exception ignored) {}

        // Lấy status nếu có
        String status = "OPEN";
        try { status = rs.getString("status"); } catch (Exception ignored) {}

        // Lấy starting_price nếu có (dùng để set startingPrice đúng)
        double startingPrice = price;
        try { startingPrice = rs.getDouble("starting_price"); } catch (Exception ignored) {}

        // Lấy seller_name nếu có (join)
        String sellerName = null;
        try { sellerName = rs.getString("seller_name"); } catch (Exception ignored) {}

        Item item = switch (type != null ? type.toUpperCase() : "ART") {
            case "ART"         -> new Art(id, name, startingPrice, endTime, sellerId, description);
            case "ELECTRONICS" -> {
                int warranty = 0;
                try { warranty = Integer.parseInt(description); } catch (Exception ignored) {}
                yield new Electronics(id, name, startingPrice, endTime, sellerId, warranty);
            }
            case "VEHICLE"     -> {
                int mileage = 0;
                try { mileage = Integer.parseInt(description); } catch (Exception ignored) {}
                yield new Vehicle(id, name, startingPrice, endTime, sellerId, mileage);
            }
            default            -> new Art(id, name, startingPrice, endTime, sellerId, description);
        };

        item.setCurrentBid(price);
        item.setBidIncrement(bidIncrement);
        if (imagePath != null && !imagePath.isBlank()) item.setImagePath(imagePath);
        if (status != null) item.setStatus(status);
        if (sellerName != null) item.setSellerName(sellerName);
        // Đảm bảo description được set đúng (cho Art)
        if (!description.isBlank()) item.setDescription(description);

        return item;
    }
}
