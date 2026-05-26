package com.auction.server.controller;

import com.auction.server.network.ClientHandler;
import com.auction.client.network.Message; 
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AutoBidService;
import com.auction.server.service.auction.AuctionQueryService;
import com.auction.server.service.auction.BidPlacementService;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * ⚡ AUCTION ROOM ENGINE CONTROLLER (ĐỘNG CƠ ĐIỀU PHỐI PHÒNG ĐẤU GIÁ REALTIME)
 * =========================================================================
 * - Trách nhiệm: Quản lý luồng tương tác thời gian thực, xử lý va chạm giá cược.
 * - Giải pháp thiết kế: Kết hợp ReentrantLock chống Race-Condition, tự động kích hoạt 
 * gia hạn SnipeGuard khi phát hiện bắn tỉa giây cuối, kích hoạt Bot tự động phản đòn.
 * - Đổi tên từ LiveBiddingController thành AuctionRoomEngineController để KHÔNG BỊ TRÙNG
 * với Controller giao diện phía Client.
 * =========================================================================
 */
public class AuctionRoomEngineController {

    private final NetworkServer server; // Hạ tầng Socket Server để duy trì kết nối mạng và Broadcast phòng
    private final AuctionQueryService auctionQueryService = new AuctionQueryService();
    private final BidPlacementService bidPlacementService = new BidPlacementService();
    private final AutoBidService autoBidService = new AutoBidService();
    private final Gson gson = new Gson();

    /**
     * Constructor nhận vào NetworkServer phục vụ việc phát sóng đồng bộ thông tin 
     * đến toàn bộ các Client đang mở xem phòng đấu giá đó.
     */
    public AuctionRoomEngineController(NetworkServer server) {
        this.server = server;
    }

    /**
     * 🏁 TRA CỨU DANH SÁCH CÁC PHÒNG ĐẤU GIÁ ĐANG DIỄN RA (ACTIVE)
     */
    public Message handleGetAuctions() {
        try {
            // Gọi QueryService lấy danh sách sản phẩm chưa hết hạn từ Database
            List<Item> items = auctionQueryService.getActiveAuctions();
            return new Message("AUCTIONS_LIST", gson.toJson(Map.of("auctions", items)));
        } catch (Exception e) {
            return new Message("ERROR", gson.toJson(Map.of("message", "Không thể tải danh sách: " + e.getMessage())));
        }
    }

    /**
     * 💰 TIẾP NHẬN YÊU CẦU ĐẶT GIÁ TỪ NGƯỜI CHƠI (HÀM CỐT LÕI)
     * Luồng xử lý: Nhận lệnh cược -> Khóa luồng (Lock) -> Kiểm tra giá -> Lưu DB -> 
     * Broadcast giá mới -> Check kích hoạt gia hạn (Anti-snipe) -> Kích hoạt Bot trả đòn.
     */
    public void handlePlaceBid(String payload, String role, String bidderId, String bidderName, ClientHandler caller) {
        
        // 1. Kiểm tra phân quyền: Chỉ tài khoản BIDDER mới được phép tham gia trả giá
        if (!"BIDDER".equalsIgnoreCase(role)) {
            caller.send(new Message("BID_RESULT", gson.toJson(Map.of("success", false, "message", "Chỉ tài khoản Người mua mới có thể trả giá."))));
            return;
        }

        // 2. Chuyển đổi payload JSON sang DTO
        PlaceBidDto dto;
        try {
            dto = gson.fromJson(payload, PlaceBidDto.class);
        } catch (Exception e) {
            caller.send(new Message("BID_RESULT", gson.toJson(Map.of("success", false, "message", "Dữ liệu cược không hợp lệ."))));
            return;
        }

        if (dto.productId() == null || dto.amount() <= 0) {
            caller.send(new Message("BID_RESULT", gson.toJson(Map.of("success", false, "message", "Giá thầu đưa ra phải lớn hơn 0 VNĐ."))));
            return;
        }

        // 3. XỬ LÝ TRANH CHẤP: Đẩy lệnh cược vào dịch vụ đặt giá chuyên biệt độc quyền ReentrantLock chống Race-Condition
        BidPlacementService.BidOutcome outcome = bidPlacementService.placeBid(dto.productId(), bidderId, dto.amount());

        // 4. Kiểm tra trạng thái cược từ Database trả về (Thành công hay bị từ chối do chậm chân)
        boolean success = outcome.result() == BidPlacementService.BidResult.SUCCESS;

        // 5. Phản hồi kết quả đặt cược ngay lập tức cho riêng cá nhân vừa bấm nút (Caller)
        caller.send(new Message("BID_RESULT", gson.toJson(Map.of("success", success, "message", outcome.message()))));

        // Nếu lượt đặt giá thất bại, dừng luồng tại đây để tiết kiệm tài nguyên mạng
        if (!success) return;

        // 6. TẠO PACKET CẬP NHẬT GIÁ: Đóng gói thông tin lượt cược thành công mới
        String bidUpdatePayload = gson.toJson(Map.of(
                "productId",  dto.productId(),
                "newBid",     outcome.newBid(),
                "bidderId",   bidderId,
                "bidderName", bidderName
        ));

        // Phát sóng tín hiệu nâng giá thời gian thực tới toàn bộ Client đang ở trong phòng này
        server.broadcastToAuction(dto.productId(), new Message("BID_UPDATE", bidUpdatePayload));

        // 7. CƠ CHẾ CHỐNG BẮN TỈA GIÂY CUỐI (SNIPEGUARD): Nếu lượt cược hợp lệ rơi vào phút chót và kích hoạt gia hạn
        if (outcome.newEndTime() != null) {
            String extPayload = gson.toJson(Map.of(
                    "productId",  dto.productId(),
                    "newEndTime", outcome.newEndTime()
            ));
            // Đồng bộ lại đồng hồ đếm ngược trên giao diện của các Client đang xem
            server.broadcastToAuction(dto.productId(), new Message("TIME_EXTENDED", extPayload));
        }

        // 8. KÍCH HOẠT BOT TỰ ĐỘNG (AUTO-BID): Sau khi người dùng đặt giá tay thành công,
        // Kích hoạt kiểm tra xem có Bot đối thủ nào cài đặt tự động nâng giá trả đòn lập tức không.
        AutoBidService.AutoBidResult autoBid = autoBidService.triggerAutoBid(dto.productId(), outcome.newBid(), bidderId);

        // Nếu tìm thấy bot cấu hình hợp lệ và nâng giá đè lên thành công
        if (autoBid != null) {
            String autoBidPayload = gson.toJson(Map.of(
                    "productId",  dto.productId(),
                    "newBid",     autoBid.newBid(),
                    "bidderId",   autoBid.bidderId(),
                    "bidderName", "[Tự động] " + autoBid.bidderId() // Tag nhận diện Bot trên UI Client
            ));
            
            // Broadcast kết quả của Bot lên phòng đấu giá cho toàn bộ người xem chứng kiến
            server.broadcastToAuction(dto.productId(), new Message("BID_UPDATE", autoBidPayload));

            // Nếu Bot trả giá cũng rơi vào phút cuối và tiếp tục kích hoạt gia hạn thời gian
            if (autoBid.newEndTime() != null) {
                String extPayload = gson.toJson(Map.of(
                        "productId",  dto.productId(),
                        "newEndTime", autoBid.newEndTime()
                ));
                server.broadcastToAuction(dto.productId(), new Message("TIME_EXTENDED", extPayload));
            }
        }
    }

    /**
     * 📊 LẤY LỊCH SỬ BƯỚC GIÁ CỦA SẢN PHẨM HIỂN THỊ LÊN ĐỒ THỊ REALTIME PHÒNG CHƠI
     */
    public Message handleGetBidHistory(String payload) {
        try {
            JsonObject json = gson.fromJson(payload, JsonObject.class);
            String productId = null;
            if (json.has("productId") && !json.get("productId").isJsonNull())
                productId = json.get("productId").getAsString();
            else if (json.has("itemId") && !json.get("itemId").isJsonNull())
                productId = json.get("itemId").getAsString();

            if (productId == null || productId.isBlank())
                return new Message("BID_HISTORY_RESPONSE", "[]");

            List<?> history = auctionQueryService.getBidHistory(productId);
            return new Message("BID_HISTORY_RESPONSE", gson.toJson(history));
        } catch (Exception e) {
            return new Message("BID_HISTORY_RESPONSE", "[]");
        }
    }

    /**
     * 👤 TRA CỨU LỊCH SỬ THAM GIA ĐẤU GIÁ CỦA CÁ NHÂN MỘT TÀI KHOẢN (TRANG PROFILE)
     */
    public Message handleGetUserBidHistory(String payload) {
        try {
            Map<String, String> requestData = gson.fromJson(payload, Map.class);
            String bidderId = requestData.get("bidderId");
            if (bidderId == null || bidderId.isBlank())
                return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
                
            List<Map<String, Object>> historyRecords = auctionQueryService.getUserBidHistory(bidderId);
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(historyRecords));
        } catch (Exception e) {
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
        }
    }

    /**
     * 📜 TRUY XUẤT LỊCH SỬ SẢN PHẨM ĐÃ ĐẶT GIAO DỊCH THÀNH CÔNG
     */
    public Message handleGetProductHistory(String payload) {
        try {
            BidderDto dto = gson.fromJson(payload, BidderDto.class);
            List<Map<String, Object>> records = auctionQueryService.getBidHistoryForBidder(dto.bidderId());
            return new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of("success", true, "records", records)));
        } catch (Exception e) {
            return new Message("ERROR", gson.toJson(Map.of("message", "Lỗi tải lịch sử sản phẩm: " + e.getMessage())));
        }
    }

    // ── CÁC ĐỐI TƯỢNG DTO NỘI BỘ ──────────────────────────
    private record PlaceBidDto(String productId, double amount) {}
    private record BidderDto(String bidderId) {}
}