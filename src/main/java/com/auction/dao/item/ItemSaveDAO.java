package com.auction.dao.item;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.auction.database.DatabaseConnection;
import com.auction.model.Entity.Item.Item;

public class ItemSaveDAO {

    public boolean saveItem(Item item) {
        String sql = "INSERT INTO items (id, name, current_price, end_time, type, seller_id, status) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, item.getId());
            pstmt.setString(2, item.getName());
            pstmt.setDouble(3, item.getStartingPrice());
            pstmt.setString(4, item.getEndTime() != null
                    ? item.getEndTime().toString() : null);
            pstmt.setString(5, item.getType());
            pstmt.setString(6, item.getSellerId());
            pstmt.setString(7, "OPEN");

            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean updatePrice(String itemId, double newPrice) {
        String sql = "UPDATE items SET current_price = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setDouble(1, newPrice);
            pstmt.setString(2, itemId);
            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean closeItem(String itemId) {
        String sql = "UPDATE items SET status = 'CLOSED' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, itemId);
            return pstmt.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}