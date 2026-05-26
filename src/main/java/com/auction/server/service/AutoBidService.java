package com.auction.server.service;

import com.auction.server.database.DatabaseConnection;
import com.auction.server.service.auction.BidPlacementService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLASS: AutoBidService — Tính năng Đấu giá tự động (Nâng cao).
 */
public class AutoBidService {

    private final BidPlacementService bidPlacementService;
    private final ConcurrentHashMap<String, Object> autoLocks = new ConcurrentHashMap<>();

    public AutoBidService() {
        this.bidPlacementService = new BidPlacementService();
    }

    public boolean registerAutoBid(String itemId, String bidderId, double maxBid, double increment) {
        String sql = "INSERT INTO auto_bids (item_id, bidder_id, max_bid, increment) "
                   + "VALUES (?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE max_bid = ?, increment = ?, active = TRUE";
                   
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
             
            ps.setString(1, itemId);
            ps.setString(2, bidderId);
            ps.setDouble(3, maxBid);
            ps.setDouble(4, increment);
            ps.setDouble(5, maxBid); 
            ps.setDouble(6, increment);
            
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[❌] Lỗi khi đăng ký Bot AutoBid cho User " + bidderId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean cancelAutoBid(String itemId, String bidderId) {
        String sql = "UPDATE auto_bids SET active = FALSE WHERE item_id = ? AND bidder_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
             
            ps.setString(1, itemId);
            ps.setString(2, bidderId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[❌] Lỗi khi hủy kích hoạt Bot của User " + bidderId + ": " + e.getMessage());
            return false;
        }
    }

    public AutoBidResult triggerAutoBid(String itemId, double newPrice, String lastBidderId) {
        Object lock = autoLocks.computeIfAbsent(itemId, k -> new Object());
        
        synchronized (lock) {
            List<AutoBidEntry> candidates = getActiveCandidates(itemId, lastBidderId, newPrice);
            if (candidates.isEmpty()) return null;

            PriorityQueue<AutoBidEntry> pq = new PriorityQueue<>(candidates.size(),
                    Comparator.comparingDouble(AutoBidEntry::maxBid).reversed()
                               .thenComparingLong(AutoBidEntry::registeredAt));

            pq.addAll(candidates);
            AutoBidEntry winner = pq.poll();
            if (winner == null) return null;

            double autoBidAmount = newPrice + winner.increment();

            if (autoBidAmount > winner.maxBid()) {
                cancelAutoBid(itemId, winner.bidderId());
                return null; 
            }

            BidPlacementService.BidOutcome outcome =
                    bidPlacementService.placeBid(itemId, winner.bidderId(), autoBidAmount);

            if (outcome.result() == BidPlacementService.BidResult.SUCCESS) {
                System.out.printf("[🤖 AutoBid] Bot [%s] nâng giá thành công: %.0f VNĐ tại sản phẩm %s%n",
                        winner.bidderId(), autoBidAmount, itemId);
                        
                return new AutoBidResult(winner.bidderId(), autoBidAmount, outcome.newEndTime());
            }

            return null;
        }
    }

    private List<AutoBidEntry> getActiveCandidates(String itemId, String excludeBidderId, double currentPrice) {
        List<AutoBidEntry> list = new ArrayList<>();
        String sql = "SELECT bidder_id, max_bid, increment, UNIX_TIMESTAMP(registered_at) as reg_ts "
                   + "FROM auto_bids "
                   + "WHERE item_id = ? AND active = TRUE AND bidder_id != ? AND max_bid > ?";
                   
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
             
            ps.setString(1, itemId);
            ps.setString(2, excludeBidderId);
            ps.setDouble(3, currentPrice);
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new AutoBidEntry(
                            rs.getString("bidder_id"),
                            rs.getDouble("max_bid"),
                            rs.getDouble("increment"),
                            rs.getLong("reg_ts")
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public record AutoBidEntry(String bidderId, double maxBid, double increment, long registeredAt) {}
    public record AutoBidResult(String bidderId, double newBid, String newEndTime) {}
}