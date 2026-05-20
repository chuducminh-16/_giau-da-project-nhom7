package com.auction.server.controller;

import com.auction.model.Entity.Auction_Bid.Auction;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AuctionService;
import com.auction.shared.network.Message;
import com.google.gson.Gson;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Nhận request từ ClientHandler, gọi AuctionService, trả kết quả.
 * Mỗi ClientHandler tạo 1 instance AuctionController riêng.
 */
public class AuctionController {

    private final AuctionService auctionService = new AuctionService();
    private final NetworkServer  server;
    private final Gson           gson = new Gson();

    public AuctionController(NetworkServer server) {
        this.server = server;
    }

    // ── Lấy danh sách đấu giá ───────────────────────────
    public Message handleGetAuctions() {
        List<Auction> list = auctionService.getActiveAuctions();
        return new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success",  true,
                "auctions", list
        )));
    }

    // ── Xem chi tiết 1 phiên ────────────────────────────
    public Message handleGetAuctionDetail(String payload) {
        AuctionIdDto dto = gson.fromJson(payload, AuctionIdDto.class);
        Auction auction = auctionService.getAuctionById(dto.auctionId());

        if (auction == null) {
            return new Message("AUCTION_DETAIL", gson.toJson(Map.of(
                    "success", false,
                    "message", "Không tìm thấy phiên đấu giá."
            )));
        }
        return new Message("AUCTION_DETAIL", gson.toJson(Map.of(
                "success", true,
                "auction", auction
        )));
    }

    // ── Thêm sản phẩm ────────────────────────────────────
    public Message handleAddProduct(String payload, String currentUserRole,
                                    String currentUserId) {
        if (!"SELLER".equals(currentUserRole)
                && !"ADMIN".equals(currentUserRole)) {
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

            Auction created = auctionService.addProduct(
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
        if (!"SELLER".equals(currentUserRole)
                && !"ADMIN".equals(currentUserRole)) {
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
        if (!"SELLER".equals(currentUserRole)
                && !"ADMIN".equals(currentUserRole)) {
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

    // ── Đặt giá — có Anti-sniping ────────────────────────
    public void handlePlaceBid(String payload, String currentUserRole,
                                String currentUserId, String currentUsername,
                                ClientHandler handler) {
        // Chỉ BIDDER được đặt giá
        if (!"BIDDER".equals(currentUserRole)) {
            handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false,
                    "message", "Chỉ Bidder mới được đặt giá."
            ))));
            return;
        }

        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);

        // Gọi service — có ReentrantLock bên trong
        AuctionService.BidOutcome outcome = auctionService.placeBid(
                dto.productId(), currentUserId, dto.amount()
        );

        switch (outcome.result()) {
            case SUCCESS -> {
                // 1. Phản hồi cho người vừa bid
                handler.send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()
                ))));

                // 2. Broadcast BID_UPDATE cho tất cả client xem phiên
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

                // 3. Anti-sniping: kiểm tra gia hạn
                AuctionService.ExtendResult ext =
                        auctionService.checkAndExtend(dto.productId());

                if (ext.wasExtended()) {
                    server.broadcastToAuction(
                            dto.productId(),
                            new Message("AUCTION_EXTENDED", gson.toJson(Map.of(
                                    "productId",  dto.productId(),
                                    "newEndTime", ext.newEndTime().toString(),
                                    "message",    "Phiên được gia hạn thêm "
                                            + ext.extendedSeconds() + " giây!"
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
        List<Auction> products =
                auctionService.getProductsBySeller(dto.sellerId());
        return new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success",  true,
                "products", products
        )));
    }

    // ── DTOs ────────────────────────────────────────────
    private record AuctionIdDto(String auctionId) {}
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