package com.auction.server.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.auction.client.network.Message;
import com.auction.server.dao.item.ItemFindDAO;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AuctionService;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;

/**
 * Xử lý request liên quan đến sản phẩm & đấu giá của SELLER và BIDDER.
 */
public class AuctionController {

    private final AuctionService auctionService = new AuctionService();
    private final ItemFindDAO    itemFindDAO    = new ItemFindDAO();
    private final NetworkServer  server;
    private final Gson           gson = new Gson();

    public AuctionController(NetworkServer server) {
        this.server = server;
    }

    // ── Lấy fresh detail của 1 sản phẩm theo itemId ───────────────────
    // Được gọi khi DetailController hoặc LiveBiddingController khởi tạo
    public Message handleGetProductDetail(String payload) {
        try {
            ProductDetailDto dto = gson.fromJson(payload, ProductDetailDto.class);
            Item item = itemFindDAO.findById(dto.itemId());

            if (item == null) {
                return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                        "success", false,
                        "message", "Không tìm thấy sản phẩm: " + dto.itemId()
                )));
            }

            return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "item",    item
            )));
        } catch (Exception e) {
            return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi server: " + e.getMessage()
            )));
        }
    }

    // ── Lấy danh sách phiên đang mở ───────────────────────────────────
    public Message handleGetAuctions() {
        List<Item> list = auctionService.getActiveAuctions();
        return new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success",  true,
                "auctions", list
        )));
    }

    // ── Lấy sản phẩm của Seller ───────────────────────────────────────
    public Message handleGetMyProducts(String payload) {
        SellerDto dto = gson.fromJson(payload, SellerDto.class);
        List<Item> items = auctionService.getProductsBySeller(dto.sellerId());
        return new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success",  true,
                "products", items
        )));
    }

    // ── Thêm sản phẩm ─────────────────────────────────────────────────
    public Message handleAddProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole)) {
            return error("ADD_PRODUCT_RESPONSE", "Bạn không có quyền đăng sản phẩm.");
        }
        ProductDto dto = gson.fromJson(payload, ProductDto.class);
        try {
            LocalDateTime startTime = dto.startTime() != null
                    ? LocalDateTime.parse(dto.startTime()) : LocalDateTime.now();
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());

            Item created = auctionService.addProduct(
                    dto.sellerId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()),
                    dto.imagePath(), startTime, endTime
            );

            if (created != null) {
                return new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                        "success", true,
                        "message", "Thêm sản phẩm thành công!",
                        "product", created
                )));
            }
            return error("ADD_PRODUCT_RESPONSE", "Không thể lưu sản phẩm.");
        } catch (Exception e) {
            return error("ADD_PRODUCT_RESPONSE", "Lỗi: " + e.getMessage());
        }
    }

    // ── Sửa sản phẩm ──────────────────────────────────────────────────
    public Message handleUpdateProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole)) {
            return error("UPDATE_PRODUCT_RESPONSE", "Bạn không có quyền sửa sản phẩm.");
        }
        UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
        try {
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());
            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()), endTime
            );
            return new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", ok,
                    "message", ok ? "Cập nhật thành công!" : "Cập nhật thất bại."
            )));
        } catch (Exception e) {
            return error("UPDATE_PRODUCT_RESPONSE", "Lỗi: " + e.getMessage());
        }
    }

    // ── Xóa sản phẩm ──────────────────────────────────────────────────
    public Message handleDeleteProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole)) {
            return error("DELETE_PRODUCT_RESPONSE", "Bạn không có quyền xóa sản phẩm.");
        }
        DeleteDto dto = gson.fromJson(payload, DeleteDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());
        return new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xoá sản phẩm!" : "Xoá thất bại."
        )));
    }

    // ── Đặt giá ───────────────────────────────────────────────────────
    public void handlePlaceBid(String payload, String currentUserRole,
                                String currentUserId, String currentUsername,
                                ClientHandler handler) {
        if (!"BIDDER".equals(currentUserRole)) {
            handler.send(error("BID_RESULT", "Chỉ Bidder mới được đặt giá."));
            return;
        }

        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);
        AuctionService.BidOutcome outcome = auctionService.placeBid(
                dto.productId(), currentUserId, dto.amount()
        );

        switch (outcome.result()) {
            case SUCCESS -> {
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()
                ))));
                server.broadcastToAuction(dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(Map.of(
                                "productId",  dto.productId(),
                                "newBid",     outcome.newBid(),
                                "bidderId",   currentUserId,
                                "bidderName", currentUsername,
                                "timestamp",  LocalDateTime.now().toString()
                        ))));
                if (outcome.newEndTime() != null) {
                    server.broadcastToAuction(dto.productId(),
                            new Message("AUCTION_EXTENDED", gson.toJson(Map.of(
                                    "productId",  dto.productId(),
                                    "newEndTime", outcome.newEndTime(),
                                    "message",    "Phiên được gia hạn thêm 60 giây!"
                            ))));
                }
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

    // ── Helper ────────────────────────────────────────────────────────
    private Message error(String type, String message) {
        return new Message(type, gson.toJson(Map.of(
                "success", false, "message", message
        )));
    }

    // ─────────────────────────────────────────────────────────────────────
    // THÊM MỚI: Xử lý request lấy lịch sử đấu giá cá nhân của Bidder
    // ─────────────────────────────────────────────────────────────────────
    public Message handleGetUserBidHistory(String payload) {
        try {
            // Đọc dữ liệu JSON gửi lên từ client
            Map<String, String> requestData = gson.fromJson(payload, Map.class);
            String bidderId = requestData.get("bidderId");

            if (bidderId == null || bidderId.isBlank()) {
                return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
            }

            // Gọi xuống AuctionService để truy vấn dữ liệu từ DB hoặc bộ nhớ máy chủ
            List<Map<String, Object>> historyRecords = auctionService.getUserBidHistory(bidderId);

            // Trả về thẳng một JsonArray trực tiếp cho Client
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(historyRecords));
        } catch (Exception e) {
            System.err.println("[AuctionController] Lỗi lấy lịch sử user: " + e.getMessage());
            // Trả về mảng rỗng phòng khi hệ thống gặp lỗi logic
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
        }
    }

    /**
     * Lấy lịch sử đấu giá của 1 bidder.
     * Gộp từ bảng bids + auctions + items + transactions để ra đủ thông tin.
     */
    public Message handleGetProductHistory(String payload) {
        try {
            BidderDto dto = gson.fromJson(payload, BidderDto.class);
            String bidderId = dto.bidderId();

            // Lấy danh sách itemId mà bidder này đã từng bid
            List<BidDAO.BidRecord> myBids = auctionService.getBidHistory(bidderId);

            // Nhóm theo itemId, lấy giá cao nhất của bidder trong từng phiên
            Map<String, Double> maxBidPerItem = new java.util.HashMap<>();
            for (BidDAO.BidRecord b : myBids) {
                maxBidPerItem.merge(b.bidderId(), b.amount(), Math::max);
                // NOTE: BidRecord.bidderId() đây thực ra là itemId — xem lại BidDAO.getBidRecords
            }

            // Query DB lấy thông tin phiên của từng item bidder đã tham gia
            List<Map<String, Object>> records =
                    auctionService.getBidHistoryForBidder(bidderId);

            return new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "records", records
            )));
        } catch (Exception e) {
            return error("Lỗi tải lịch sử: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // REGISTER AUTO-BID (FIX: ban goc THIEU method nay)
    // ─────────────────────────────────────────────────────────────────────
    public Message handleRegisterAutoBid(String payload, String bidderId) {
        try {
            AutoBidDto dto = gson.fromJson(payload, AutoBidDto.class);

            if (dto.maxBid() <= 0)
                return autoBidResult(false, "maxBid phai > 0.");
            if (dto.increment() <= 0)
                return autoBidResult(false, "Buoc gia (increment) phai > 0.");

            boolean ok = autoBidService.registerAutoBid(
                    dto.itemId(), bidderId, dto.maxBid(), dto.increment());

            return autoBidResult(ok,
                    ok ? "Dang ky auto-bid thanh cong." : "Dang ky auto-bid that bai.");
        } catch (Exception e) {
            return autoBidResult(false, "Loi: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // CANCEL AUTO-BID (FIX: ban goc THIEU method nay)
    // ─────────────────────────────────────────────────────────────────────
    public Message handleCancelAutoBid(String payload, String bidderId) {
        try {
            CancelAutoBidDto dto = gson.fromJson(payload, CancelAutoBidDto.class);
            boolean ok = autoBidService.cancelAutoBid(dto.itemId(), bidderId);
            return autoBidResult(ok,
                    ok ? "Da huy auto-bid." : "Khong tim thay auto-bid de huy.");
        } catch (Exception e) {
            return autoBidResult(false, "Loi: " + e.getMessage());
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
    // DTOs noi bo
    // ─────────────────────────────────────────────────────────────────────
    private record GetMyProductsDto(String sellerId) {}
    private record PlaceBidDto(String productId, String bidderId, double amount) {}
    private record GetBidHistoryDto(String productId) {}
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
    private record BidderDto(String bidderId) {}
    // ── DTOs ──────────────────────────────────────────────────────────
    private record ProductDetailDto(String itemId) {}
    private record SellerDto(String sellerId) {}
    private record ProductDto(String sellerId, String name, String description,
                              String startPrice, String bidIncrement,
                              String imagePath, String startTime, String endTime) {}
    private record DeleteDto(String productId) {}
    private record PlaceBidDto(String productId, double amount) {}
}

