package com.auction.server.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.auction.client.network.Message;
import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.transaction.TransactionDAO;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.WalletService;
import com.google.gson.Gson;

/**
 * Tự động kiểm tra và đóng các phiên đấu giá hết giờ.
 */
public class AuctionScheduler {

    private final AuctionDAO     auctionDAO     = new AuctionDAO();
    private final BidDAO         bidDAO         = new BidDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();
    private final Gson           gson           = new Gson();

    private final NetworkServer server;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auction-scheduler");
                t.setDaemon(true);
                return t;
            });

    public AuctionScheduler(NetworkServer server) {
        this.server = server;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::scanExpiredAuctions, 5, 10, TimeUnit.SECONDS);
        System.out.println("[Scheduler] Đã khởi động. Kiểm tra phiên hết giờ mỗi 10 giây.");
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void scanExpiredAuctions() {
        try {
            List<Map<String, Object>> expiredList = auctionDAO.findExpiredOpen();
            for (Map<String, Object> auction : expiredList) {
                closeAuction(auction);
            }
        } catch (Exception e) {
            System.err.println("[Scheduler] Lỗi khi scan phiên hết giờ: " + e.getMessage());
        }
    }

    private void closeAuction(Map<String, Object> auction) {
        // ✅ FIX 1: Dùng ((Number) ...).longValue() thay vì (long) để tránh ClassCastException
        // Gson deserialize số nguyên lớn thành Double, không phải Long
        long   auctionId = ((Number) auction.get("id")).longValue();
        String itemId    = (String) auction.get("itemId");

        System.out.printf("[Scheduler] Đóng phiên #%d (item: %s)%n", auctionId, itemId);

        Map<String, Object> winner = auctionDAO.findWinner(auctionId);

        if (winner != null) {
            String winnerId   = (String) winner.get("bidderId");
            String username   = (String) winner.get("username");
            // ✅ FIX 2: Dùng ((Number) ...).doubleValue() thay vì (double) để tránh ClassCastException
            double finalPrice = ((Number) winner.get("finalPrice")).doubleValue();

            transactionDAO.saveTransaction(itemId, winnerId, finalPrice);
            auctionDAO.updateStatus(auctionId, "FINISHED");
            new WalletService().deductBalance(winnerId, finalPrice);  // THÊM DÒNG NÀY

            System.out.printf("[Scheduler] Phiên #%d kết thúc. Winner: %s, Giá: %.0f%n",
                    auctionId, username, finalPrice);

            String payload = gson.toJson(Map.of(
                    "auctionId",  auctionId,
                    "itemId",     itemId,
                    "winnerId",   winnerId,
                    "winnerName", username,
                    "finalPrice", finalPrice,
                    "message",    String.format("🏆 Phiên đấu giá kết thúc! %s thắng với giá %.0f VNĐ", username, finalPrice)
            ));
            server.broadcastToAuction(itemId, new Message("AUCTION_ENDED", payload));

        } else {
            auctionDAO.updateStatus(auctionId, "CANCELED");
            System.out.printf("[Scheduler] Phiên #%d hủy (không có bid).%n", auctionId);

            String payload = gson.toJson(Map.of(
                    "auctionId", auctionId,
                    "itemId",    itemId != null ? itemId : "",
                    "message",   "Phiên đấu giá đã kết thúc mà không có người tham gia."
            ));
            server.broadcastToAuction(itemId != null ? itemId : "", new Message("AUCTION_ENDED", payload));
        }
    }
}