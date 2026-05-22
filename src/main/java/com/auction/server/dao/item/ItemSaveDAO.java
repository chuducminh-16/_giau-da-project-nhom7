package com.auction.server.dao.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.Item.Item;

public class ItemSaveDAO {

    public boolean saveItem(Item item) {
        String sqlWithDesc = "INSERT INTO items (id, name, current_price, starting_price, end_time, type, seller_id, status, description, bid_increment, image_path) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlNoDesc   = "INSERT INTO items (id, name, current_price, end_time, type, seller_id, status) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sqlWithDesc)) {
                pstmt.setString(1, item.getId());
                pstmt.setString(2, item.getName());
                pstmt.setDouble(3, item.getStartingPrice());
                pstmt.setDouble(4, item.getStartingPrice());
                pstmt.setString(5, item.getEndTime() != null ? item.getEndTime().toString() : null);
                pstmt.setString(6, item.getType());
                pstmt.setString(7, item.getSellerId());
                pstmt.setString(8, item.getStatus() != null ? item.getStatus() : "OPEN");
                pstmt.setString(9, item.getDescription() != null ? item.getDescription() : "");
                pstmt.setDouble(10, item.getBidIncrement());
                pstmt.setString(11, item.getImagePath() != null ? item.getImagePath() : "");
                return pstmt.executeUpdate() > 0;
            } catch (Exception e) {
                // Fallback: DB không có các cột mở rộng
                try (PreparedStatement pstmt = conn.prepareStatement(sqlNoDesc)) {
                    pstmt.setString(1, item.getId());
                    pstmt.setString(2, item.getName());
                    pstmt.setDouble(3, item.getStartingPrice());
                    pstmt.setString(4, item.getEndTime() != null ? item.getEndTime().toString() : null);
                    pstmt.setString(5, item.getType());
                    pstmt.setString(6, item.getSellerId());
                    pstmt.setString(7, item.getStatus() != null ? item.getStatus() : "OPEN");
                    return pstmt.executeUpdate() > 0;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── FIX: thêm method updateItem() mà AuctionService gọi tới ──────────
    public boolean updateItem(String productId, String name, String description,
                              double startPrice, double bidIncrement,
                              LocalDateTime endTime) {
        String sql = "UPDATE items SET name=?, current_price=?, end_time=?, description=?, bid_increment=? WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setDouble(2, startPrice);
            ps.setString(3, endTime != null ? endTime.toString() : null);
            ps.setString(4, description != null ? description : "");
            ps.setDouble(5, bidIncrement);
            ps.setString(6, productId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── FIX: thêm method deleteItem() mà AuctionService gọi tới ──────────
    public boolean deleteItem(String productId) {
        String delBids    = "DELETE FROM bids WHERE item_id=?";
        String delAuction = "DELETE FROM auctions WHERE item_id=?";
        String delItem    = "DELETE FROM items WHERE id=?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(delBids)) {
                    ps.setString(1, productId); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(delAuction)) {
                    ps.setString(1, productId); ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(delItem)) {
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