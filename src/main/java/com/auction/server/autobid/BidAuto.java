package com.auction.server.autobid;

import com.auction.client.network.Message;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AutoBidService;
import com.auction.server.service.AuctionService;
import com.google.gson.Gson;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * BidAuto — Event Bus độc lập xử lý Auto-Bidding.
 *
 * Cách hoạt động:
 *   1. ClientHandler.handlePlaceBid() publish RecordBid vào queue
 *   2. BidAuto chạy trên thread riêng, liên tục đọc queue
 *   3. Mỗi event → gọi AutoBidService.triggerAutoBid()
 *   4. Nếu có auto-bid → broadcast BID_UPDATE tới tất cả client
 *
 * Không cần sửa ClientHandler hay bất kỳ file cũ nào.
 * Chỉ cần:
 *   - ClientHandler gọi: BidAuto.getInstance().publish(event)
 *   - NetworkServer gọi: BidAuto.getInstance().start(server)
 */
public class BidAuto {

    // ── Singleton ──────────────────────────────────────────────────────────
    private static final class Holder {
        static final BidAuto INSTANCE = new BidAuto();
    }

    public static BidAuto getInstance() {
        return Holder.INSTANCE;
    }

    private BidAuto() {}

    // ── Fields ─────────────────────────────────────────────────────────────

    // Queue không giới hạn — ClientHandler publish, worker thread consume
    private final BlockingQueue<RecordBid> queue =
            new LinkedBlockingQueue<>();

    // 1 thread xử lý tuần tự để tránh race condition giữa các auto-bid
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "auto-bid-worker");
                t.setDaemon(true);
                return t;
            });

    private NetworkServer server;
    private AutoBidService autoBidService;
    private final Gson gson = new Gson();

    private volatile boolean running = false;

    // ── Start / Stop ───────────────────────────────────────────────────────

    /**
     * Khởi động BidAuto. Gọi từ NetworkServer.start() sau scheduler.start().
     *
     * @param server NetworkServer để broadcast BID_UPDATE sau auto-bid
     */
    public void start(NetworkServer server) {
        if (running) return;
        this.server = server;
        this.autoBidService = new AutoBidService(new AuctionService());
        this.running = true;

        worker.submit(this::processLoop);
        System.out.println("[BidAuto] Đã khởi động. Sẵn sàng xử lý auto-bid.");
    }

    public void stop() {
        running = false;
        worker.shutdownNow();
    }

    // ── Publish ────────────────────────────────────────────────────────────

    /**
     * ClientHandler gọi method này sau mỗi bid thành công.
     * Không block — chỉ thêm vào queue và return ngay.
     *
     * Cách dùng trong ClientHandler (thêm 1 dòng sau broadcast):
     *   BidAuto.getInstance().publish(
     *       new RecordBid(dto.productId(), currentUser.getId(), outcome.newBid())
     *   );
     */
    public void publish(RecordBid event) {
        if (running) {
            queue.offer(event);
        }
    }

    // ── Process Loop (chạy trên worker thread) ─────────────────────────────

    private void processLoop() {
        while (running) {
            try {
                RecordBid event = queue.take();
                handleEvent(event);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[BidAuto] Lỗi xử lý event: " + e.getMessage());
            }
        }
    }

    // ── Handle Event ───────────────────────────────────────────────────────

    private void handleEvent(RecordBid event) {
        System.out.printf("[BidAuto] Nhận event: productId=%s, bidderId=%s, price=%.0f%n",
                event.productId(), event.bidderId(), event.newPrice());

        // Trigger auto-bid cho các đối thủ
        AutoBidService.AutoBidResult result = autoBidService.triggerAutoBid(
                event.productId(),
                event.newPrice(),
                event.bidderId()
        );

        // Nếu có auto-bid thành công → broadcast cho tất cả client đang xem phiên
        if (result != null && server != null) {
            System.out.printf("[BidAuto] Auto-bid thành công: bidderId=%s, newBid=%.0f%n",
                    result.bidderId(), result.newBid());

            String payload = gson.toJson(Map.of(
                    "productId",  event.productId(),
                    "newBid",     result.newBid(),
                    "bidderId",   result.bidderId(),
                    "bidderName", "[Auto] " + result.bidderId(),
                    "timestamp",  java.time.LocalDateTime.now().toString(),
                    "isAutoBid",  true
            ));

            server.broadcastToAuction(
                    event.productId(),
                    new Message("BID_UPDATE", payload)
            );

            // Nếu auto-bid kích hoạt anti-sniping (newEndTime != null)
            if (result.newEndTime() != null) {
                String timePayload = gson.toJson(Map.of(
                        "productId",  event.productId(),
                        "newEndTime", result.newEndTime()
                ));
                server.broadcastToAuction(
                        event.productId(),
                        new Message("TIME_EXTENDED", timePayload)
                );
            }

            // Đệ quy: auto-bid vừa xong có thể trigger auto-bid của người khác
            publish(new RecordBid(
                    event.productId(),
                    result.bidderId(),
                    result.newBid()
            ));
        }
    }
}
