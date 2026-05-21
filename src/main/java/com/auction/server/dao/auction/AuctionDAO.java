package com.auction.server.dao.auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.auction.server.database.DatabaseConnection;

/**
 * AuctionDAO — thêm phương thức findExpiredOpen() cho AuctionScheduler.
 *
 * PATCH: copy toàn bộ class gốc và bổ sung findExpiredOpen() ở cuối.
 * Chú ý: đặt file này vào đúng package com.auction.server.dao.auction,
 * THAY THẾ file AuctionDAO.java cũ.
 */
public class AuctionDAO {

    // ── Lưu phiên mới ────────────────────────────────────────────────────
    public boolean saveAuction(long auctionId, String itemId, String sellerId,
                                double startingPrice, String endTime) {
        String sql = "INSERT INTO auctions (id, item_id, seller_id, current_price, status, end_time) "
                   + "VALUES (?, ?, ?, ?, 'OPEN', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ps.setString(2, itemId);
            ps.setString(3, sellerId);
            ps.setDouble(4, startingPrice);
            ps.setString(5, endTime);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Tìm theo id ──────────────────────────────────────────────────────
    public Map<String, Object> findById(long auctionId) {
        String sql = "SELECT a.*, i.name as item_name, i.current_price "
                   + "FROM auctions a JOIN items i ON a.item_id = i.id "
                   + "WHERE a.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ── Lấy tất cả OPEN/RUNNING chưa hết giờ ────────────────────────────
    public List<Map<String, Object>> findAllOpen() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name as item_name, i.current_price "
                   + "FROM auctions a JOIN items i ON a.item_id = i.id "
                   + "WHERE a.status IN ('OPEN','RUNNING') AND a.end_time > NOW() "
                   + "ORDER BY a.end_time ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Lấy tất cả phiên (dành cho Admin) ───────────────────────────────
    public List<Map<String, Object>> findAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name as item_name, i.current_price "
                   + "FROM auctions a JOIN items i ON a.item_id = i.id "
                   + "ORDER BY a.end_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ── Cập nhật trạng thái ──────────────────────────────────────────────
    public boolean updateStatus(long auctionId, String newStatus) {
        String sql = "UPDATE auctions SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setLong(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Cập nhật giá khi có bid mới ──────────────────────────────────────
    public boolean updateCurrentPrice(long auctionId, double newPrice) {
        String sql = "UPDATE auctions SET current_price = ?, status = 'RUNNING' WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, newPrice);
            ps.setLong(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    // ── Tìm winner (bid cao nhất) ────────────────────────────────────────
    public Map<String, Object> findWinner(long auctionId) {
        String sql = "SELECT b.bidder_id, u.username, b.bid_price "
                   + "FROM bids b "
                   + "JOIN auctions a ON b.item_id = a.item_id "
                   + "JOIN users u ON b.bidder_id = u.id "
                   + "WHERE a.id = ? "
                   + "ORDER BY b.bid_price DESC LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Map<String, Object> w = new HashMap<>();
                w.put("bidderId",   rs.getString("bidder_id"));
                w.put("username",   rs.getString("username"));
                w.put("finalPrice", rs.getDouble("bid_price"));
                return w;
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ── Tìm theo seller ──────────────────────────────────────────────────
    public List<Map<String, Object>> findBySeller(String sellerId) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name as item_name, i.current_price "
                   + "FROM auctions a JOIN items i ON a.item_id = i.id "
                   + "WHERE a.seller_id = ? ORDER BY a.end_time DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sellerId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    // ═══════════════════════════════════════════════════════
    // MỚI — dùng bởi AuctionScheduler
    // ═══════════════════════════════════════════════════════

    /**
     * Lấy các phiên OPEN/RUNNING đã quá endTime — cần đóng lại.
     * Scheduler gọi method này mỗi 10 giây.
     */
    public List<Map<String, Object>> findExpiredOpen() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT a.*, i.name as item_name, i.current_price "
                   + "FROM auctions a JOIN items i ON a.item_id = i.id "
                   + "WHERE a.status IN ('OPEN','RUNNING') AND a.end_time <= NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (Exception e) { e.printStackTrace(); }
        return list;
    }

    /**
     * Gia hạn endTime — dùng cho Anti-sniping.
     * Cộng thêm {@code extraSeconds} giây vào end_time hiện tại.
     */
    public boolean extendEndTime(long auctionId, int extraSeconds) {
        String sql = "UPDATE auctions "
                   + "SET end_time = DATE_ADD(end_time, INTERVAL ? SECOND) "
                   + "WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, extraSeconds);
            ps.setLong(2, auctionId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { e.printStackTrace(); return false; }
    }

    /**
     * Lấy endTime hiện tại của 1 phiên (dùng cho Anti-sniping check).
     */
    public String getEndTime(long auctionId) {
        String sql = "SELECT end_time FROM auctions WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, auctionId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("end_time");
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // ── Helper: map ResultSet → Map ──────────────────────────────────────
    private Map<String, Object> mapRow(ResultSet rs) throws Exception {
        Map<String, Object> row = new HashMap<>();
        row.put("id",           rs.getLong("id"));
        row.put("itemId",       rs.getString("item_id"));
        row.put("itemName",     rs.getString("item_name"));
        row.put("sellerId",     rs.getString("seller_id"));
        row.put("currentPrice", rs.getDouble("current_price"));
        row.put("status",       rs.getString("status"));
        row.put("endTime",      rs.getString("end_time"));
        return row;
    }
}