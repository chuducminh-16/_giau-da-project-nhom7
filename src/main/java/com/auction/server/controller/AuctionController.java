package com.auction.server.controller;

import com.auction.shared.model.Entity.Item.Item;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AuctionService;
import com.auction.client.network.Message;
import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AuctionController {

    private final AuctionService auctionService = new AuctionService();
    private final NetworkServer  server;
    private final Gson           gson = new Gson();

    public AuctionController(NetworkServer server) {
        this.server = server;
    }

    // ── Lấy danh sách đấu giá ───────────────────────────
    public Message handleGetAuctions() {
        List<Item> list = auctionService.getActiveAuctions(); // fix: Item thay vì Auction
        return new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success",  true,
                "auctions", list
        )));
    }

    // ── Thêm sản phẩm ────────────────────────────────────
    public Message handleAddProduct(String payload, String currentUserRole,
                                    String currentUserId) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole)) {
            return new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Bạn không có quyền đăng sản phẩm."
            )));
        }

        ProductDto dto = gson.fromJson(payload, ProductDto.class);
        try {
            LocalDateTime startTime = (dto.startTime() != null)
                    ? LocalDateTime.parse(dto.startTime())
                    : LocalDateTime.now();
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());

            // fix: addProduct trả về Item, không phải Auction
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
            return new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Không thể lưu sản phẩm."
            )));

        } catch (Exception e) {
            return new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi: " + e.getMessage()
            )));
        }
    }

    // ── Sửa sản phẩm ─────────────────────────────────────
    public Message handleUpdateProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole)) {
            return new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Bạn không có quyền sửa sản phẩm."
            )));
        }

        UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
        try {
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());
            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()),
                    endTime
            );
            return new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", ok,
                    "message", ok ? "Cập nhật thành công!" : "Cập nhật thất bại."
            )));
        } catch (Exception e) {
            return new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi: " + e.getMessage()
            )));
        }
    }

    // ── Xóa sản phẩm ─────────────────────────────────────
    public Message handleDeleteProduct(String payload, String currentUserRole) {
        if (!"SELLER".equals(currentUserRole) && !"ADMIN".equals(currentUserRole)) {
            return new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Bạn không có quyền xóa sản phẩm."
            )));
        }

        DeleteDto dto = gson.fromJson(payload, DeleteDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());
        return new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xoá sản phẩm!" : "Xoá thất bại."
        )));
    }

    // ── Đặt giá ──────────────────────────────────────────
    public void handlePlaceBid(String payload, String currentUserRole,
                                String currentUserId, String currentUsername,
                                ClientHandler handler) {
        if (!"BIDDER".equals(currentUserRole)) {
            handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false,
                    "message", "Chỉ Bidder mới được đặt giá."
            ))));
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
                // Broadcast cho tất cả client đang xem phiên
                // fix: bỏ checkAndExtend (không tồn tại), anti-sniping đã xử lý
                // bên trong AuctionService.placeBid() rồi, chỉ cần broadcast kết quả
                server.broadcastToAuction(
                        dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(Map.of(
                                "productId",  dto.productId(),
                                "newBid",     outcome.newBid(),
                                "bidderId",   currentUserId,
                                "bidderName", currentUsername,
                                "timestamp",  LocalDateTime.now().toString()
                        )))
                );
                // Nếu có gia hạn (newEndTime != null), broadcast thêm
                if (outcome.newEndTime() != null) {
                    server.broadcastToAuction(
                            dto.productId(),
                            new Message("AUCTION_EXTENDED", gson.toJson(Map.of(
                                    "productId",  dto.productId(),
                                    "newEndTime", outcome.newEndTime(),
                                    "message",    "Phiên được gia hạn thêm 60 giây!"
                            )))
                    );
                }
            }
            case PRICE_TOO_LOW -> handler.send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success",    false,
                            "message",    outcome.message(),
                            "currentBid", outcome.newBid()
                    ))));
            case AUCTION_ENDED -> handler.send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success", false,
                            "message", "Phiên đấu giá đã kết thúc."
                    ))));
            case AUCTION_NOT_FOUND -> handler.send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success", false,
                            "message", "Phiên đấu giá không tồn tại."
                    ))));
            default -> handler.send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success", false,
                            "message", "Lỗi hệ thống."
                    ))));
        }
    }

    // ── Lấy sản phẩm của Seller ──────────────────────────
    public Message handleGetMyProducts(String payload) {
        SellerDto dto = gson.fromJson(payload, SellerDto.class);
        List<Item> products = auctionService.getProductsBySeller(dto.sellerId()); // fix: Item
        return new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success",  true,
                "products", products
        )));
    }

    // ── DTOs ────────────────────────────────────────────
    private record ProductDto(
            String sellerId, String name, String description,
            String startPrice, String bidIncrement,
            String imagePath, String startTime, String endTime) {}
    private record UpdateProductDto(
            String productId, String name, String description,
            String startPrice, String bidIncrement, String endTime) {}
    private record DeleteDto(String productId) {}
    private record SellerDto(String sellerId) {}
    private record PlaceBidDto(String productId, double amount) {}
}