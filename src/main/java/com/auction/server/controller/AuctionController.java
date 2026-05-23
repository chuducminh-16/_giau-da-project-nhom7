package com.auction.server.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.auction.client.network.Message;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.item.ItemFindDAO;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AuctionService;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;

/**
 * AuctionController — thêm 2 method còn thiếu:
 *   1. handleGetBidHistory()     — lịch sử 1 sản phẩm (LiveBiddingController)
 *   2. handleGetUserBidHistory() — lịch sử cá nhân Bidder (BidHistoryController)
 */
public class AuctionController {

    private final AuctionService auctionService = new AuctionService();
    private final ItemFindDAO    itemFindDAO    = new ItemFindDAO();
    private final BidDAO         bidDAO         = new BidDAO();
    private final NetworkServer  server;
    private final Gson           gson = new Gson();

    public AuctionController(NetworkServer server) {
        this.server = server;
    }

    // ── GET_PRODUCT_DETAIL ────────────────────────────────────────────────
    public Message handleGetProductDetail(String payload) {
        try {
            ProductDetailDto dto = gson.fromJson(payload, ProductDetailDto.class);
            Item item = itemFindDAO.findById(dto.itemId());
            if (item == null) {
                return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                        "success", false,
                        "message", "Không tìm thấy sản phẩm: " + dto.itemId())));
            }
            return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                    "success", true, "item", item)));
        } catch (Exception e) {
            return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "Lỗi server: " + e.getMessage())));
        }
    }

    // ── GET_AUCTIONS ──────────────────────────────────────────────────────
    public Message handleGetAuctions() {
        List<Item> list = auctionService.getActiveAuctions();
        return new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success", true, "auctions", list)));
    }

    // ── GET_MY_PRODUCTS ───────────────────────────────────────────────────
    public Message handleGetMyProducts(String payload) {
        SellerDto dto = gson.fromJson(payload, SellerDto.class);
        List<Item> items = auctionService.getProductsBySeller(dto.sellerId());
        return new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success", true, "products", items)));
    }

    // ── GET_BID_HISTORY — lịch sử của 1 sản phẩm (LiveBiddingController) ─
    public Message handleGetBidHistory(String payload) {
        try {
            BidHistoryDto dto = gson.fromJson(payload, BidHistoryDto.class);
            // Hỗ trợ cả field "auctionId" lẫn "productId"
            String id = dto.auctionId() != null ? dto.auctionId() : dto.productId();
            List<BidDAO.BidRecord> history = auctionService.getBidHistory(id);
            return new Message("BID_HISTORY", gson.toJson(Map.of(
                    "auctionId", id,
                    "bids",      history)));
        } catch (Exception e) {
            return error("BID_HISTORY", "Không thể tải lịch sử: " + e.getMessage());
        }
    }

    // ── GET_USER_BID_HISTORY — lịch sử cá nhân Bidder (BidHistoryController) ─
    public Message handleGetUserBidHistory(String payload) {
        try {
            Map<?, ?> req = gson.fromJson(payload, Map.class);
            String bidderId = (String) req.get("bidderId");

            if (bidderId == null || bidderId.isBlank()) {
                return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
            }

            // Gọi AuctionService — method đã có sẵn trong AuctionService của bạn
            List<Map<String, Object>> records = auctionService.getBidHistoryForBidder(bidderId);
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(records));

        } catch (Exception e) {
            System.err.println("[AuctionController] Lỗi lấy lịch sử user: " + e.getMessage());
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
        }
    }

    // ── ADD_PRODUCT ───────────────────────────────────────────────────────
    public Message handleAddProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole))
            return error("ADD_PRODUCT_RESPONSE", "Bạn không có quyền đăng sản phẩm.");

        ProductDto dto = gson.fromJson(payload, ProductDto.class);
        try {
            LocalDateTime startTime = dto.startTime() != null
                    ? LocalDateTime.parse(dto.startTime()) : LocalDateTime.now();
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());

            Item created = auctionService.addProduct(
                    dto.sellerId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()),
                    dto.imagePath(), startTime, endTime);

            if (created != null) {
                return new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                        "success", true,
                        "message", "Thêm sản phẩm thành công!",
                        "product", created)));
            }
            return error("ADD_PRODUCT_RESPONSE", "Không thể lưu sản phẩm.");
        } catch (Exception e) {
            return error("ADD_PRODUCT_RESPONSE", "Lỗi: " + e.getMessage());
        }
    }

    // ── UPDATE_PRODUCT ────────────────────────────────────────────────────
    public Message handleUpdateProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole))
            return error("UPDATE_PRODUCT_RESPONSE", "Bạn không có quyền sửa sản phẩm.");

        UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
        try {
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());
            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()), endTime);
            return new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", ok,
                    "message", ok ? "Cập nhật thành công!" : "Cập nhật thất bại.")));
        } catch (Exception e) {
            return error("UPDATE_PRODUCT_RESPONSE", "Lỗi: " + e.getMessage());
        }
    }

    // ── DELETE_PRODUCT ────────────────────────────────────────────────────
    public Message handleDeleteProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole))
            return error("DELETE_PRODUCT_RESPONSE", "Bạn không có quyền xóa sản phẩm.");

        DeleteDto dto = gson.fromJson(payload, DeleteDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());
        return new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xoá sản phẩm!" : "Xoá thất bại.")));
    }

    // ── PLACE_BID ─────────────────────────────────────────────────────────
    public void handlePlaceBid(String payload, String currentUserRole,
                                String currentUserId, String currentUsername,
                                ClientHandler handler) {
        if (!"BIDDER".equals(currentUserRole)) {
            handler.send(error("BID_RESULT", "Chỉ Bidder mới được đặt giá."));
            return;
        }

        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);
        AuctionService.BidOutcome outcome = auctionService.placeBid(
                dto.productId(), currentUserId, dto.amount());

        switch (outcome.result()) {
            case SUCCESS -> {
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()))));

                server.broadcastToAuction(dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(Map.of(
                                "productId",  dto.productId(),
                                "newBid",     outcome.newBid(),
                                "bidderId",   currentUserId,
                                "bidderName", currentUsername,
                                "timestamp",  LocalDateTime.now().toString()))));

                if (outcome.newEndTime() != null) {
                    // Tính newSeconds để client cập nhật countdown đúng
                    try {
                        LocalDateTime newEnd = LocalDateTime.parse(
                                outcome.newEndTime().replace(" ", "T"));
                        long newSeconds = java.time.Duration
                                .between(LocalDateTime.now(), newEnd).getSeconds();
                        server.broadcastToAuction(dto.productId(),
                                new Message("AUCTION_EXTENDED", gson.toJson(Map.of(
                                        "auctionId",  dto.productId(),
                                        "newSeconds", newSeconds,
                                        "newEndTime", outcome.newEndTime(),
                                        "message",    "Phiên được gia hạn thêm 60 giây!"))));
                    } catch (Exception e) {
                        System.err.println("[AuctionController] Lỗi parse newEndTime: " + e.getMessage());
                    }
                }
            }
            case PRICE_TOO_LOW ->
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success",    false,
                        "message",    outcome.message(),
                        "currentBid", outcome.newBid()))));
            case AUCTION_ENDED ->
                handler.send(error("BID_RESULT", "Phiên đấu giá đã kết thúc."));
            case AUCTION_NOT_FOUND ->
                handler.send(error("BID_RESULT", "Sản phẩm không tồn tại."));
            default ->
                handler.send(error("BID_RESULT", "Lỗi hệ thống."));
        }
    }

    // ── Helper ────────────────────────────────────────────────────────────
    private Message error(String type, String message) {
        return new Message(type, gson.toJson(Map.of("success", false, "message", message)));
    }

    // ── DTOs ──────────────────────────────────────────────────────────────
    private record ProductDetailDto(String itemId) {}
    private record SellerDto(String sellerId) {}
    private record BidHistoryDto(String auctionId, String productId) {}
    private record ProductDto(String sellerId, String name, String description,
                              String startPrice, String bidIncrement,
                              String imagePath, String startTime, String endTime) {}
    private record UpdateProductDto(String productId, String name, String description,
                                    String startPrice, String bidIncrement, String endTime) {}
    private record DeleteDto(String productId) {}
    private record PlaceBidDto(String productId, double amount) {}
}
