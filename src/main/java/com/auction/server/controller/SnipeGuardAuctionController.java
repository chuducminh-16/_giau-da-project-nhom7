package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.item.ItemFindDAO;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.SnipeGuardService;
import com.auction.server.service.SnipeGuardService.SnipeGuardResult;
// SỬA ĐỔI: Chuyển sang import dịch vụ đặt giá mới thay cho AuctionService cũ đã bị phân rã
import com.auction.server.service.auction.BidPlacementService;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * =========================================================================
 * THƯ MỤC: com.auction.server.controller
 * CLASS: SnipeGuardAuctionController
 * * CƠ CHẾ HOẠT ĐỘNG (Chống bắn tỉa phút chót):
 * 1. Thay thế luồng đặt giá thông thường khi hệ thống bật tính năng SnipeGuard.
 * 2. Sau khi ghi nhận đặt giá thành công -> Kích hoạt SnipeGuardService để 
 * kiểm tra xem lượt cược có nằm trong "khung giờ nhạy cảm" (ví dụ: < 30 giây cuối) không.
 * 3. Nếu thỏa mãn -> Tự động gia hạn thêm thời gian cho phiên đấu giá và 
 * phát sóng (Broadcast) thông báo cho toàn bộ phòng đấu giá cùng biết.
 * =========================================================================
 */
public class SnipeGuardAuctionController {

    // SỬA ĐỔI: Thay thế dịch vụ tổng hợp cũ bằng dịch vụ đơn nhiệm quản lý đặt cược mới
    private final BidPlacementService bidPlacementService = new BidPlacementService();
    
    private final SnipeGuardService snipeGuardService = new SnipeGuardService();
    private final AuctionDAO         auctionDAO         = new AuctionDAO();
    private final ItemFindDAO        itemFindDAO        = new ItemFindDAO();
    private final NetworkServer      server;
    private final Gson               gson = new Gson();

    /**
     * Constructor nhận hạ tầng NetworkServer để phục vụ việc đẩy gói tin thời gian thực.
     */
    public SnipeGuardAuctionController(NetworkServer server) {
        this.server = server;
    }

    // ─────────────────────────────────────────────────────────────────────
    // XỬ LÝ LỆNH ĐẶT GIÁ CÓ TÍCH HỢP CHỐNG BẮN TỈA
    // ─────────────────────────────────────────────────────────────────────
    public void handlePlaceBid(String payload, String currentUserRole,
                               String currentUserId, String currentUsername,
                               ClientHandler handler) {

        // Kiểm tra phân quyền: Chỉ tài khoản có vai trò BIDDER (Người mua) mới được trả giá
        if (!"BIDDER".equals(currentUserRole)) {
            handler.send(error("BID_RESULT", "Chỉ tài khoản Người mua mới có quyền đặt giá."));
            return;
        }

        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);

        // ── Bước 1: Đặt giá (Gọi dịch vụ mới có luồng ReentrantLock bảo vệ an toàn) ──
        BidPlacementService.BidOutcome outcome =
                bidPlacementService.placeBid(dto.productId(), currentUserId, dto.amount());

        // FIX LỖI: Chuyển các case từ String sang đúng định dạng Enum của hệ thống
        switch (outcome.result()) {

            case SUCCESS -> {
                // ── Bước 2: Phản hồi tin vui thành công về riêng cho người bấm nút ──
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Chúc mừng! Bạn đã đặt giá thành công.",
                        "newBid",  outcome.newBid()
                ))));

                // ── Bước 3: Phát sóng (Broadcast) giá tiền mới cập nhật cho toàn bộ phòng đấu giá ──
                server.broadcastToAuction(dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(Map.of(
                                "productId",  dto.productId(),
                                "newBid",     outcome.newBid(),
                                "bidderId",   currentUserId,
                                "bidderName", currentUsername,
                                "timestamp",  LocalDateTime.now().toString()
                        ))));

                // ── Bước 4: Kích hoạt bộ kiểm tra chống bắn tỉa (SnipeGuard) ──
                handleSnipeGuard(dto.productId(), outcome.newBid());
            }

            case PRICE_TOO_LOW ->
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success",    false,
                        "message",    outcome.message(),
                        "currentBid", outcome.newBid()
                ))));

            case AUCTION_ENDED ->
                handler.send(error("BID_RESULT", "Rất tiếc, phiên đấu giá này đã kết thúc thời gian trả giá."));

            case AUCTION_NOT_FOUND ->
                handler.send(error("BID_RESULT", "Sản phẩm hoặc phiên đấu giá không tồn tại trên hệ thống."));

            default ->
                handler.send(error("BID_RESULT", "Hệ thống gặp sự cố bất ngờ khi xử lý lượt cược."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOGIC KIỂM TRA KHUNG GIỜ VÀ GIA HẠN PHIÊN (SNIPE GUARD CORES)
    // ─────────────────────────────────────────────────────────────────────
    private void handleSnipeGuard(String productId, double newBid) {
        // Tra cứu mã số định danh phiên (auctionId) dựa trên mã sản phẩm (productId)
        long auctionId = findAuctionIdByProductId(productId);
        if (auctionId == -1) {
            System.err.println("[⚠️ SnipeGuard] Không thể tìm thấy auctionId tương ứng cho sản phẩm: " + productId);
            return;
        }

        // Gọi dịch vụ SnipeGuardService để tính toán khoảng cách thời gian và thực hiện gia hạn nếu cần
        SnipeGuardResult result = snipeGuardService.checkAndExtend(auctionId);

        System.out.println("[🛡️ Hệ thống SnipeGuard] Tiến trình kiểm tra: " + result);

        // Nếu lượt đặt cược hợp lệ rơi vào giây phút chót và kích hoạt gia hạn thành công
        if (result.extended) {
            // Thiết lập gói dữ liệu thông báo kéo dài thời gian
            String broadcastPayload = gson.toJson(Map.of(
                    "productId",      productId,
                    "auctionId",      auctionId,
                    "newEndTime",     result.newEndTimeStr,
                    "extendedBy",     result.extendedBySeconds,
                    "wasSecondsLeft", result.secondsLeftBeforeExtend,
                    "message",        String.format(
                            "⏰ Phát hiện đặt giá phút chót! Phiên được tự động gia hạn thêm %d giây. (Thời gian còn lại lúc cược: %d giây)",
                            result.extendedBySeconds,
                            result.secondsLeftBeforeExtend)
            ));

            // Phát súng đồng bộ tín hiệu "AUCTION_EXTENDED" ra toàn phòng đấu giá để cập nhật lại UI đồng hồ đếm ngược
            server.broadcastToAuction(productId,
                    new Message("AUCTION_EXTENDED", broadcastPayload));

            System.out.printf("[🛡️ SnipeGuard] ✅ Broadcast hoàn tất -> Sản phẩm=%s, Mốc giờ mới=%s%n",
                    productId, result.newEndTimeStr);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // TRUY VẤN MÃ AUCTION_ID TỪ MÃ ITEM_ID (PRODUCT_ID)
    // ─────────────────────────────────────────────────────────────────────
    private long findAuctionIdByProductId(String productId) {
        try {
            // Đầu tiên, quét nhanh qua danh sách các phiên đấu giá đang mở (OPEN) để tối ưu hiệu năng
            List<Map<String, Object>> auctions = auctionDAO.findAllOpen();
            for (Map<String, Object> a : auctions) {
                String itemId = (String) a.get("itemId");
                if (itemId == null) itemId = (String) a.get("item_id");
                if (productId.equals(itemId)) {
                    Object idObj = a.get("id");
                    if (idObj instanceof Number n) return n.longValue();
                }
            }
            
            // Cơ chế dự phòng (Fallback): Nếu không thấy ở danh sách đang mở, quét toàn bộ DB (bao gồm cả phiên FINISHED)
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
            System.err.println("[❌] Gặp lỗi khi truy vấn tìm kiếm cặp ID Sản phẩm/Phiên: " + e.getMessage());
        }
        return -1; // Trả về -1 nếu hoàn toàn không tìm thấy bản ghi phù hợp
    }

    // ─────────────────────────────────────────────────────────────────────
    // HÀM TIỆN ÍCH ĐÓNG GÓI NHANH BẢN TIN LỖI
    // ─────────────────────────────────────────────────────────────────────
    private Message error(String type, String message) {
        return new Message(type, gson.toJson(Map.of(
                "success", false, "message", message
        )));
    }

    // Đối tượng DTO nội bộ nhận cấu trúc gói cược từ Client gửi lên
    private record PlaceBidDto(String productId, double amount) {}
}