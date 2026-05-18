package com.auction.server.dao;

import com.auction.model.Entity.Auction_Bid.Auction;
import com.auction.model.Entity.Item.Item;
import com.auction.model.Entity.User.Seller;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductDAO {

    private final Connection conn =
            DBConnection.getInstance().getConnection();

    // ── Lấy tất cả sản phẩm của 1 seller ──────────────
    public List<Auction> findBySeller(String sellerId) {
        String sql = "SELECT * FROM items WHERE seller_id = ?" +
                " ORDER BY id DESC";
        List<Auction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[ProductDAO] findBySeller: " + e.getMessage());
        }
        return list;
    }

    // ── Lấy tất cả sản phẩm đang OPEN ─────────────────
    public List<Auction> findAllActive() {
        String sql = "SELECT * FROM items WHERE status = 'OPEN'" +
                " ORDER BY end_time ASC";
        List<Auction> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            System.err.println("[ProductDAO] findAllActive: " + e.getMessage());
        }
        return list;
    }

    // ── Thêm sản phẩm mới ─────────────────────────────
    public Auction save(String sellerId, String name,
                        String description, double startPrice,
                        double bidIncrement, String imagePath,
                        LocalDateTime startTime, LocalDateTime endTime) {

        String stringId = UUID.randomUUID().toString();
        // Dùng hashCode để chuyển String UUID → long id cho Auction
        // (chỉ dùng nội bộ để tạo object trả về client)
        long longId = Math.abs(stringId.hashCode());

        String sql = """
                INSERT INTO items
                    (id, seller_id, name, description,
                     starting_price, current_price, bid_increment,
                     image_path, status, start_time, end_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, stringId);
            ps.setString(2, sellerId);
            ps.setString(3, name);
            ps.setString(4, description);
            ps.setDouble(5, startPrice);
            ps.setDouble(6, startPrice);  // current_price ban đầu = startPrice
            ps.setDouble(7, bidIncrement);
            ps.setString(8, imagePath);
            ps.setString(9,  startTime != null
                    ? startTime.toString() : null);
            ps.setString(10, endTime.toString());
            ps.executeUpdate();

            // Tạo Item tạm để truyền vào Auction constructor
            // Item chứa thông tin sản phẩm cơ bản từ DB
            Item item = buildItem(stringId, name, description,
                    startPrice, bidIncrement, imagePath);

            // Tạo Seller tạm với sellerId
            Seller seller = buildSeller(sellerId);

            // Dùng đúng constructor của Auction
            return new Auction(longId, item, seller,
                    startPrice, endTime);

        } catch (SQLException e) {
            System.err.println("[ProductDAO] save: " + e.getMessage());
            return null;
        }
    }

    // ── Cập nhật sản phẩm ─────────────────────────────
    public boolean update(String productId, String name,
                          String description, double startPrice,
                          double bidIncrement, LocalDateTime endTime) {
        String sql = """
                UPDATE items
                SET name           = ?,
                    description    = ?,
                    starting_price = ?,
                    bid_increment  = ?,
                    end_time       = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setDouble(3, startPrice);
            ps.setDouble(4, bidIncrement);
            ps.setString(5, endTime.toString());
            ps.setString(6, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ProductDAO] update: " + e.getMessage());
            return false;
        }
    }

    // ── Xoá sản phẩm ──────────────────────────────────
    public boolean delete(String productId) {
        String sql = "DELETE FROM items WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ProductDAO] delete: " + e.getMessage());
            return false;
        }
    }

    // ── Tìm theo id ───────────────────────────────────
    public Auction findById(String productId) {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (SQLException e) {
            System.err.println("[ProductDAO] findById: " + e.getMessage());
        }
        return null;
    }

    // ── Map ResultSet → Auction object ────────────────
    private Auction mapRow(ResultSet rs) throws SQLException {
        String stringId = rs.getString("id");
        // String UUID → long dùng hashCode (nhất quán với save())
        long longId = Math.abs(stringId.hashCode());

        LocalDateTime endTime = rs.getString("end_time") != null
                ? LocalDateTime.parse(rs.getString("end_time")) : null;
        LocalDateTime startTime = rs.getString("start_time") != null
                ? LocalDateTime.parse(rs.getString("start_time")) : null;

        String name        = rs.getString("name");
        String description = rs.getString("description");
        double startPrice  = rs.getDouble("starting_price");
        double bidIncrement= rs.getDouble("bid_increment");
        String imagePath   = rs.getString("image_path");
        String sellerId    = rs.getString("seller_id");
        String status      = rs.getString("status");

        // Tạo Item và Seller tạm từ dữ liệu DB
        Item   item   = buildItem(stringId, name, description,
                startPrice, bidIncrement, imagePath);
        Seller seller = buildSeller(sellerId);

        // Tạo Auction bằng constructor đúng
        Auction auction = new Auction(longId, item, seller,
                startPrice, endTime);
        // Cập nhật status từ DB (constructor mặc định là "OPEN")
        auction.setStatus(status);

        return auction;
    }

    // ── Helper: tạo Item tạm từ dữ liệu DB ───────────
    // Dùng anonymous subclass vì Item là abstract
    private Item buildItem(String id, String name, String description,
                           double startingPrice, double bidIncrement,
                           String imagePath) {
        return new Item(id, name, startingPrice, null, null) {
            @Override
            public String getType() {
                return "General";
            }

            @Override
            public String showDetails() {
                return description + " | Bước giá: "
                        + String.format("%.0f VNĐ", bidIncrement)
                        + " | Ảnh: " + imagePath;
            }

            @Override
            public String getCategory() {
                return "General";
            }

            @Override
            public String getSummary() {
                return name + " — " + String.format("%.0f VNĐ", startingPrice);
            }
        };
    }

    // ── Helper: tạo Seller tạm chỉ có id ─────────────
    // Dùng để truyền vào Auction constructor mà không cần query bảng users
    private Seller buildSeller(String sellerId) {
        // Tạo Seller với thông tin tối thiểu
        // password truyền "" vì DAO không cần dùng
        return new Seller(sellerId, "seller_" + sellerId, "", "", 5.0);
    }
}