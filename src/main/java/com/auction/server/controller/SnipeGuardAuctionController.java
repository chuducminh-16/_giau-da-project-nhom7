package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.item.ItemFindDAO;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AuctionService;
import com.auction.server.service.SnipeGuardService;
import com.auction.server.service.SnipeGuardService.SnipeGuardResult;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * SnipeGuardAuctionController
 * ═══════════════════════════════════════════════════════════════════
 *  FILE MỚI — THAY THẾ handlePlaceBid() trong AuctionController.java
 *
 *  Khác với AuctionController gốc ở chỗ:
 *    1. Sau khi placeBid() SUCCESS → gọi SnipeGuardService.checkAndExtend()
 *    2. Nếu đã gia hạn → broadcast thêm message AUCTION_EXTENDED
 *       đến tất cả client đang xem phiên
 *    3. Log rõ ràng ra terminal server
 *
 *  Cách tích hợp:
 *    - Trong ClientHandler.java (file gốc), thay dòng:
 *        auctionController.handlePlaceBid(p, role(), currentUser.getId(), ...)
 *      bằng:
 *        snipeGuardController.handlePlaceBid(p, role(), currentUser.getId(), ...)
 *
 *    - Khai báo thêm field trong ClientHandler:
 *        private final SnipeGuardAuctionController snipeGuardController
 *            = new SnipeGuardAuctionController(server);
 * ═══════════════════════════════════════════════════════════════════
 */
public class SnipeGuardAuctionController {

    private final AuctionService    auctionService    = new AuctionService();
    private final SnipeGuardService snipeGuardService = new SnipeGuardService();
    private final AuctionDAO        auctionDAO        = new AuctionDAO();
    private final ItemFindDAO       itemFindDAO       = new ItemFindDAO();
    private final NetworkServer     server;
    private final Gson              gson = new Gson();

    public SnipeGuardAuctionController(NetworkServer server) {
        this.server = server;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  handlePlaceBid — gọi từ ClientHandler khi nhận PLACE_BID
    // ─────────────────────────────────────────────────────────────────────
    public void handlePlaceBid(String payload, String currentUserRole,
                                String currentUserId, String currentUsername,
                                ClientHandler handler) {

        // Chỉ BIDDER được đặt giá
        if (!"BIDDER".equals(currentUserRole)) {
            handler.send(error("BID_RESULT", "Chỉ Bidder mới được đặt giá."));
            return;
        }

        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);

        // ── Bước 1: Đặt giá (logic gốc) ──────────────────────────────────
        AuctionService.BidOutcome outcome =
                auctionService.placeBid(dto.productId(), currentUserId, dto.amount());

        switch (outcome.result()) {

            case SUCCESS -> {
                // ── Bước 2: Thông báo cho người đặt ─────────────────────
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()
                ))));

                // ── Bước 3: Broadcast BID_UPDATE cho tất cả người xem ────
                server.broadcastToAuction(dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(Map.of(
                                "productId",  dto.productId(),
                                "newBid",     outcome.newBid(),
                                "bidderId",   currentUserId,
                                "bidderName", currentUsername,
                                "timestamp",  LocalDateTime.now().toString()
                        ))));

                // ── Bước 4: Kiểm tra & gia hạn SnipeGuard ────────────────
                handleSnipeGuard(dto.productId(), outcome.newBid());
            }

            case PRICE_TOO_LOW ->
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success",    false,
                        "message",    outcome.message(),
                        "currentBid", outcome.newBid()
                ))));

            case AUCTION_ENDED ->
                handler.send(error("BID_RESULT", "Phiên đấu giá đã kết thúc."));

            case AUCTION_NOT_FOUND ->
                handler.send(error("BID_RESULT", "Sản phẩm không tồn tại."));

            default ->
                handler.send(error("BID_RESULT", "Lỗi hệ thống."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  handleSnipeGuard — kiểm tra và broadcast AUCTION_EXTENDED nếu cần
    // ─────────────────────────────────────────────────────────────────────
    private void handleSnipeGuard(String productId, double newBid) {
        // Tìm auctionId từ productId (itemId)
        long auctionId = findAuctionIdByProductId(productId);
        if (auctionId == -1) {
            System.err.println("[SnipeGuard] Không tìm thấy auctionId cho productId=" + productId);
            return;
        }

        // Gọi SnipeGuardService
        SnipeGuardResult result = snipeGuardService.checkAndExtend(auctionId);

        System.out.println("[SnipeGuard] " + result);

        if (result.extended) {
            // ── Broadcast AUCTION_EXTENDED cho tất cả client đang xem ────
            String broadcastPayload = gson.toJson(Map.of(
                    "productId",    productId,
                    "auctionId",    auctionId,
                    "newEndTime",   result.newEndTimeStr,
                    "extendedBy",   result.extendedBySeconds,
                    "wasSecondsLeft", result.secondsLeftBeforeExtend,
                    "message",      String.format(
                            "⏰ Phiên được gia hạn thêm %d giây! (bid lúc còn %d giây)",
                            result.extendedBySeconds,
                            result.secondsLeftBeforeExtend)
            ));

            server.broadcastToAuction(productId,
                    new Message("AUCTION_EXTENDED", broadcastPayload));

            System.out.printf("[SnipeGuard] ✅ Broadcast AUCTION_EXTENDED → productId=%s, newEnd=%s%n",
                    productId, result.newEndTimeStr);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  findAuctionIdByProductId — truy vấn auctionId từ itemId
    // ─────────────────────────────────────────────────────────────────────
    private long findAuctionIdByProductId(String productId) {
        try {
            List<Map<String, Object>> auctions = auctionDAO.findAllOpen();
            for (Map<String, Object> a : auctions) {
                String itemId = (String) a.get("itemId");
                if (itemId == null) itemId = (String) a.get("item_id");
                if (productId.equals(itemId)) {
                    Object idObj = a.get("id");
                    if (idObj instanceof Number n) return n.longValue();
                }
            }
            // Fallback: tìm trong tất cả phiên (kể cả FINISHED)
            List<Map<String, Object>> allAuctions = auctionDAO.findAll();
            for (Map<String, Object> a : allAuctions) {
                String itemId = (String) a.get("itemId");
                if (itemId == null) itemId = (String) a.get("item_id");
                if (productId.equals(itemId)) {
                    Object idObj = a.get("id");
                    if (idObj instanceof Number n) return n.longValue();
                }
            }
        } catch (Exception e) {
            System.err.println("[SnipeGuard] findAuctionIdByProductId lỗi: " + e.getMessage());
        }
        return -1;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helper
    // ─────────────────────────────────────────────────────────────────────
    private Message error(String type, String message) {
        return new Message(type, gson.toJson(Map.of(
                "success", false, "message", message
        )));
    }

    private record PlaceBidDto(String productId, double amount) {}
}
