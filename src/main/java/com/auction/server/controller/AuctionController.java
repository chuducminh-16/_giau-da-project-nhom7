package com.auction.server.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.auction.client.network.Message;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AutoBidService;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;

/**
 * Server-side AuctionController.
 *
 * FIX:
 *   1. handleGetAuctions(): wrap response thành {"auctions":[...]}
 *      để HomeController parse đúng (trước trả Array thô → parse lỗi)
 *   2. handleGetMyProducts(): wrap thành {"products":[...]}
 *      để ManageProductController parse đúng
 *   3. handleGetBidHistory(): dùng đúng key "productId" từ payload
 *      (LiveBidding gửi productId, BidHistory gửi bidderId → route riêng)
 */
public class AuctionController {

    private final NetworkServer  server;
    private final AuctionService auctionService  = new AuctionService();
    private final AutoBidService autoBidService  = new AutoBidService(auctionService);
    private final Gson           gson            = new Gson();

    public AuctionController(NetworkServer server) {
        this.server = server;
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET AUCTIONS
    // FIX: Wrap thành {"auctions":[...]} thay vì trả JsonArray thô
    // HomeController đang parse: root.get("auctions") → cần key này
    // ─────────────────────────────────────────────────────────────────────
    public Message handleGetAuctions() {
        try {
            List<Item> items = auctionService.getActiveAuctions();
            return new Message("AUCTIONS_RESPONSE", gson.toJson(Map.of(
                    "auctions", items
            )));
        } catch (Exception e) {
            return error("Không thể tải danh sách đấu giá: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET MY PRODUCTS
    // FIX: Wrap thành {"products":[...]} thay vì Array thô
    // ManageProductController đã được fix để parse JsonArray,
    // nhưng wrap thêm cho nhất quán
    // ─────────────────────────────────────────────────────────────────────
    public Message handleGetMyProducts(String payload) {
        try {
            GetMyProductsDto dto = gson.fromJson(payload, GetMyProductsDto.class);
            List<Item> items = auctionService.getProductsBySeller(dto.sellerId());
            // Trả JsonArray trực tiếp — ManageProductController đã fix để parse Array
            return new Message("MY_PRODUCTS_RESPONSE", gson.toJson(items));
        } catch (Exception e) {
            return error("Không thể tải sản phẩm: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADD PRODUCT
    // ─────────────────────────────────────────────────────────────────────
    public Message handleAddProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Chỉ Seller mới có thể thêm sản phẩm.");
        }
        try {
            AddProductDto dto = gson.fromJson(payload, AddProductDto.class);

            if (dto.name() == null || dto.name().isBlank())
                return error("Tên sản phẩm không được rỗng.");
            if (dto.startPrice() <= 0)
                return error("Giá khởi điểm phải > 0.");
            if (dto.endTime() == null || dto.endTime().isBlank())
                return error("Thời gian kết thúc không được rỗng.");

            LocalDateTime endTime;
            try {
                endTime = LocalDateTime.parse(dto.endTime().replace(" ", "T"));
            } catch (Exception e) {
                return error("Định dạng thời gian kết thúc không hợp lệ.");
            }
            if (endTime.isBefore(LocalDateTime.now()))
                return error("Thời gian kết thúc đã qua.");

            LocalDateTime startTime = (dto.startTime() != null && !dto.startTime().isBlank())
                    ? LocalDateTime.parse(dto.startTime().replace(" ", "T"))
                    : LocalDateTime.now();

            Item item = auctionService.addProduct(
                    dto.sellerId(),
                    dto.name(),
                    dto.description() != null ? dto.description() : "",
                    dto.startPrice(),
                    dto.bidIncrement() > 0 ? dto.bidIncrement() : 1000.0,
                    dto.imagePath() != null ? dto.imagePath() : "",
                    startTime,
                    endTime
            );

            if (item == null) return error("Không thể thêm sản phẩm. Vui lòng thử lại.");

            return new Message("ADD_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", true, "item", item)));
        } catch (Exception e) {
            e.printStackTrace();
            return error("Lỗi hệ thống: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE PRODUCT
    // ─────────────────────────────────────────────────────────────────────
    public Message handleUpdateProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Không có quyền cập nhật sản phẩm.");
        }
        try {
            UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
            if (dto.productId() == null || dto.productId().isBlank())
                return error("productId không được rỗng.");

            LocalDateTime endTime = LocalDateTime.parse(dto.endTime().replace(" ", "T"));

            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    dto.startPrice(), dto.bidIncrement(), endTime);

            return new Message("UPDATE_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", ok,
                            "message", ok ? "Cập nhật thành công." : "Cập nhật thất bại.")));
        } catch (Exception e) {
            return error("Lỗi cập nhật sản phẩm: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // DELETE PRODUCT
    // ─────────────────────────────────────────────────────────────────────
    public Message handleDeleteProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Không có quyền xóa sản phẩm.");
        }
        try {
            DeleteProductDto dto = gson.fromJson(payload, DeleteProductDto.class);
            boolean ok = auctionService.deleteProduct(dto.productId());
            return new Message("DELETE_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", ok,
                            "message", ok ? "Đã xóa sản phẩm." : "Không thể xóa.")));
        } catch (Exception e) {
            return error("Lỗi xóa sản phẩm: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PLACE BID — thread-safe, broadcast realtime
    // ─────────────────────────────────────────────────────────────────────
    public void handlePlaceBid(String payload, String role,
                               String bidderId, String bidderName,
                               ClientHandler caller) {
        if (!"BIDDER".equalsIgnoreCase(role)) {
            caller.send(new Message("BID_RESULT",
                    gson.toJson(Map.of("success", false,
                            "message", "Chỉ Bidder mới có thể đặt giá."))));
            return;
        }

        PlaceBidDto dto;
        try {
            dto = gson.fromJson(payload, PlaceBidDto.class);
        } catch (Exception e) {
            caller.send(new Message("BID_RESULT",
                    gson.toJson(Map.of("success", false, "message", "Dữ liệu không hợp lệ."))));
            return;
        }

        if (dto.productId() == null || dto.amount() <= 0) {
            caller.send(new Message("BID_RESULT",
                    gson.toJson(Map.of("success", false, "message", "Dữ liệu bid không hợp lệ."))));
            return;
        }

        AuctionService.BidOutcome outcome =
                auctionService.placeBid(dto.productId(), bidderId, dto.amount());

        boolean success = outcome.result() == AuctionService.BidResult.SUCCESS;

        caller.send(new Message("BID_RESULT",
                gson.toJson(Map.of("success", success, "message", outcome.message()))));

        if (!success) return;

        // ── Broadcast BID_UPDATE đến tất cả client đang xem phiên này ──
        String bidUpdatePayload = gson.toJson(Map.of(
                "productId",  dto.productId(),
                "newBid",     outcome.newBid(),
                "bidderName", bidderName
        ));
        server.broadcastToAuction(dto.productId(), new Message("BID_UPDATE", bidUpdatePayload));

        // ── Broadcast TIME_EXTENDED nếu anti-sniping kích hoạt ──
        if (outcome.newEndTime() != null) {
            String extPayload = gson.toJson(Map.of(
                    "productId",  dto.productId(),
                    "newEndTime", outcome.newEndTime()
            ));
            server.broadcastToAuction(dto.productId(),
                    new Message("TIME_EXTENDED", extPayload));
            System.out.printf("[AuctionController] Anti-sniping: phiên %s gia hạn -> %s%n",
                    dto.productId(), outcome.newEndTime());
        }

        // ── Trigger auto-bid của đối thủ ──
        AutoBidService.AutoBidResult autoBid =
                autoBidService.triggerAutoBid(dto.productId(), outcome.newBid(), bidderId);

        if (autoBid != null) {
            String autoBidPayload = gson.toJson(Map.of(
                    "productId",  dto.productId(),
                    "newBid",     autoBid.newBid(),
                    "bidderName", autoBid.bidderId() + " (auto)"
            ));
            server.broadcastToAuction(dto.productId(),
                    new Message("BID_UPDATE", autoBidPayload));

            if (autoBid.newEndTime() != null) {
                String extPayload = gson.toJson(Map.of(
                        "productId",  dto.productId(),
                        "newEndTime", autoBid.newEndTime()
                ));
                server.broadcastToAuction(dto.productId(),
                        new Message("TIME_EXTENDED", extPayload));
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET BID HISTORY (cho LiveBiddingController — dùng productId)
    // ─────────────────────────────────────────────────────────────────────
    public Message handleGetBidHistory(String payload) {
        try {
            GetBidHistoryDto dto = gson.fromJson(payload, GetBidHistoryDto.class);

            // FIX: LiveBidding gửi "productId", BidHistory gửi "bidderId"
            // Route này chỉ xử lý productId (LiveBidding room)
            if (dto.productId() != null && !dto.productId().isBlank()) {
                List<BidDAO.BidRecord> history = auctionService.getBidHistory(dto.productId());
                return new Message("BID_HISTORY_RESPONSE", gson.toJson(history));
            }

            // Nếu có bidderId → lấy lịch sử theo bidder (BidHistoryController)
            if (dto.bidderId() != null && !dto.bidderId().isBlank()) {
                return handleGetBidHistoryForBidder(dto.bidderId());
            }

            return error("Thiếu productId hoặc bidderId.");
        } catch (Exception e) {
            return error("Không thể tải lịch sử: " + e.getMessage());
        }
    }

    /**
     * Lấy lịch sử đấu giá theo bidder — dùng cho BidHistoryController.
     * Trả về {"success": true, "records": [...]}
     */
    private Message handleGetBidHistoryForBidder(String bidderId) {
        try {
            java.util.List<java.util.Map<String, Object>> records =
                    auctionService.getBidHistoryForBidder(bidderId);
            return new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "records", records
            )));
        } catch (Exception e) {
            return new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi tải lịch sử: " + e.getMessage()
            )));
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // REGISTER AUTO-BID
    // ─────────────────────────────────────────────────────────────────────
    public Message handleRegisterAutoBid(String payload, String bidderId) {
        try {
            AutoBidDto dto = gson.fromJson(payload, AutoBidDto.class);

            if (dto.maxBid() <= 0)
                return autoBidResult(false, "maxBid phải > 0.");
            if (dto.increment() <= 0)
                return autoBidResult(false, "Bước giá (increment) phải > 0.");

            boolean ok = autoBidService.registerAutoBid(
                    dto.itemId(), bidderId, dto.maxBid(), dto.increment());

            return autoBidResult(ok,
                    ok ? "Đăng ký auto-bid thành công." : "Đăng ký auto-bid thất bại.");
        } catch (Exception e) {
            return autoBidResult(false, "Lỗi: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CANCEL AUTO-BID
    // ─────────────────────────────────────────────────────────────────────
    public Message handleCancelAutoBid(String payload, String bidderId) {
        try {
            CancelAutoBidDto dto = gson.fromJson(payload, CancelAutoBidDto.class);
            boolean ok = autoBidService.cancelAutoBid(dto.itemId(), bidderId);
            return autoBidResult(ok,
                    ok ? "Đã hủy auto-bid." : "Không tìm thấy auto-bid để hủy.");
        } catch (Exception e) {
            return autoBidResult(false, "Lỗi: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────
    private Message error(String msg) {
        return new Message("ERROR", gson.toJson(Map.of("message", msg)));
    }

    private Message autoBidResult(boolean success, String message) {
        return new Message("AUTO_BID_RESULT",
                gson.toJson(Map.of("success", success, "message", message)));
    }

    // ─────────────────────────────────────────────────────────────────────
    // DTOs nội bộ
    // ─────────────────────────────────────────────────────────────────────
    private record GetMyProductsDto(String sellerId) {}
    private record PlaceBidDto(String productId, String bidderId, double amount) {}

    // FIX: thêm cả bidderId để 1 DTO xử lý được cả 2 loại request
    private record GetBidHistoryDto(String productId, String bidderId) {}

    private record AutoBidDto(String itemId, double maxBid, double increment) {}
    private record CancelAutoBidDto(String itemId) {}

    private record AddProductDto(
            String sellerId, String name, String description,
            double startPrice, double bidIncrement,
            String imagePath, String startTime, String endTime) {}

    private record UpdateProductDto(
            String productId, String name, String description,
            double startPrice, double bidIncrement, String endTime) {}

    private record DeleteProductDto(String productId) {}
}