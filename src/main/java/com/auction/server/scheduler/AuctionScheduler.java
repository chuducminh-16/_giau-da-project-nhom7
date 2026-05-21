package com.auction.server.scheduler;

import com.auction.client.network.Message;
import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.transaction.TransactionDAO;
import com.auction.server.network.NetworkServer;
import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tự động kiểm tra và đóng các phiên đấu giá hết giờ.
 *
 * Chức năng bắt buộc theo đề bài:
 *   - Tự động đóng phiên khi hết thời gian
 *   - Xác định người thắng cuộc
 *   - Chuyển trạng thái: RUNNING → FINISHED / CANCELED
 *   - Broadcast kết quả đến tất cả client đang xem phiên (Observer)
 *
 * Chạy mỗi 10 giây để scan các phiên đã quá endTime.
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

    /** Khởi động scheduler — gọi từ NetworkServer.start(). */
    public void start() {
        // Delay 5s trước khi chạy lần đầu, sau đó cứ 10s chạy 1 lần.
        scheduler.scheduleAtFixedRate(this::scanExpiredAuctions, 5, 10, TimeUnit.SECONDS);
        System.out.println("[Scheduler] Đã khởi động. Kiểm tra phiên hết giờ mỗi 10 giây.");
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    // ── Core: scan tất cả phiên đã quá endTime ────────────────────────────

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

    /**
     * Đóng 1 phiên đấu giá:
     * 1. Tìm bid cao nhất → xác định winner
     * 2. Nếu có winner → lưu transaction, chuyển trạng thái FINISHED
     * 3. Nếu không có bid → chuyển CANCELED
     * 4. Broadcast AUCTION_ENDED đến tất cả client đang xem phiên đó
     */
    private void closeAuction(Map<String, Object> auction) {
        long   auctionId = (long)   auction.get("id");
        String itemId    = (String) auction.get("itemId");

        System.out.printf("[Scheduler] Đóng phiên #%d (item: %s)%n", auctionId, itemId);

        Map<String, Object> winner = auctionDAO.findWinner(auctionId);

        if (winner != null) {
            String winnerId   = (String) winner.get("bidderId");
            String username   = (String) winner.get("username");
            double finalPrice = (double) winner.get("finalPrice");

            // Lưu transaction
            transactionDAO.saveTransaction(itemId, winnerId, finalPrice);

            // Cập nhật trạng thái phiên → FINISHED
            auctionDAO.updateStatus(auctionId, "FINISHED");

            System.out.printf("[Scheduler] Phiên #%d kết thúc. Winner: %s, Giá: %.0f%n",
                    auctionId, username, finalPrice);

            // Broadcast kết quả cho tất cả client đang xem phiên này
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
            // Không có bid nào → hủy phiên
            auctionDAO.updateStatus(auctionId, "CANCELED");
            System.out.printf("[Scheduler] Phiên #%d hủy (không có bid).%n", auctionId);

            String payload = gson.toJson(Map.of(
                    "auctionId", auctionId,
                    "itemId",    itemId,
                    "message",   "Phiên đấu giá đã kết thúc mà không có người tham gia."
            ));
            server.broadcastToAuction(itemId, new Message("AUCTION_ENDED", payload));
        }
    }
}
