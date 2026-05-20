package com.auction.server.network;

import com.auction.server.service.AuctionService;
import com.auction.shared.network.Message;
import com.google.gson.Gson;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Chạy ngầm mỗi 5 giây, kiểm tra phiên nào hết giờ.
 * Nếu có → đóng phiên → broadcast AUCTION_ENDED cho client.
 *
 * Đây là phần "tự động đóng phiên" trong yêu cầu bắt buộc.
 * Cũng là nơi trigger Anti-sniping sau mỗi bid thành công.
 */
public class AuctionScheduler {

    private final NetworkServer  server;
    private final AuctionService auctionService = new AuctionService();
    private final Gson           gson           = new Gson();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "auction-scheduler");
                t.setDaemon(true); // tự tắt khi app đóng
                return t;
            });

    public AuctionScheduler(NetworkServer server) {
        this.server = server;
    }

    // ── Gọi 1 lần trong NetworkServer.start() ──────────
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::checkExpiredAuctions,
                0,   // chạy ngay lập tức
                5,   // lặp lại mỗi 5 giây
                TimeUnit.SECONDS
        );
        System.out.println("[AuctionScheduler] Bắt đầu"
                + " — kiểm tra mỗi 5 giây.");
    }

    public void stop() {
        scheduler.shutdown();
        System.out.println("[AuctionScheduler] Đã dừng.");
    }

    // ── Kiểm tra và đóng phiên hết giờ ─────────────────
    private void checkExpiredAuctions() {
        try {
            // Backend trả danh sách phiên vừa bị đóng
            List<AuctionService.AuctionCloseResult> closed =
                    auctionService.closeExpiredAuctions();

            for (AuctionService.AuctionCloseResult result : closed) {
                System.out.println("[AuctionScheduler] Phiên kết thúc: "
                        + result.auctionId()
                        + " | Người thắng: " + result.winnerName());

                // Broadcast kết quả cho tất cả client xem phiên này
                server.broadcastToAuction(
                        result.auctionId(),
                        new Message("AUCTION_ENDED", gson.toJson(Map.of(
                                "auctionId",  result.auctionId(),
                                "winnerId",   result.winnerId()   != null
                                        ? result.winnerId()   : "",
                                "winnerName", result.winnerName() != null
                                        ? result.winnerName() : "Không có",
                                "finalPrice", result.finalPrice(),
                                "message",    result.winnerId() == null
                                        ? "Phiên kết thúc, không có người đặt giá."
                                        : "Phiên kết thúc! Người thắng: "
                                        + result.winnerName()
                        )))
                );
            }
        } catch (Exception e) {
            // Không để exception làm crash scheduler thread
            System.err.println("[AuctionScheduler] Lỗi: " + e.getMessage());
        }
    }
}
