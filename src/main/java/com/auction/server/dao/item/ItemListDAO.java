package com.auction.server.dao.item;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.Item.Item;

public class ItemListDAO {

    // Lấy tất cả sản phẩm
    public List<Item> findAll() {
        return query("SELECT * FROM items", null);
    }

    // Chỉ lấy sản phẩm đang mở
    public List<Item> findAllOpen() {
        return query("SELECT * FROM items WHERE status = 'OPEN' AND end_time > NOW()", null);
    }

    // Lấy sản phẩm theo người bán
    public List<Item> findBySeller(String sellerId) {
        return query("SELECT * FROM items WHERE seller_id = ?", sellerId);
    }

    // Dùng chung để tránh lặp code
    private List<Item> query(String sql, String param) {
        List<Item> items = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            if (param != null) pstmt.setString(1, param);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                items.add(ItemFindDAO.mapRow(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return items;
    }
}