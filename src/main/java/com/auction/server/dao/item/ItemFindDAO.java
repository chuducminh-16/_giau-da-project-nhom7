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
                ? LocalDateTime.parse(endTimeStr) : null;

        return switch (type) {
            case "ART"         -> new Art(id, name, price, endTime, sellerId, "");
            case "ELECTRONICS" -> new Electronics(id, name, price, endTime, sellerId, 0);
            case "VEHICLE"     -> new Vehicle(id, name, price, endTime, sellerId, 0);
            default            -> new Art(id, name, price, endTime, sellerId, "");
        };
    }
}