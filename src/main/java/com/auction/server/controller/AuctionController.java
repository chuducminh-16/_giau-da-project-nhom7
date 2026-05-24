package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.network.ClientHandler;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AutoBidService;
import com.auction.server.dao.bid.BidDAO;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AuctionController {

    private final NetworkServer  server;
    private final AuctionService auctionService  = new AuctionService();
    private final AutoBidService autoBidService  = new AutoBidService(auctionService);
    private final Gson           gson            = new Gson();

    public AuctionController(NetworkServer server) {
        this.server = server;
    }

    // GET AUCTIONS
    public Message handleGetAuctions() {
        try {
            List<Item> items = auctionService.getActiveAuctions();
            return new Message("AUCTIONS_LIST", gson.toJson(Map.of("auctions", items)));
        } catch (Exception e) {
            return error("Không thể tải danh sách đấu giá: " + e.getMessage());
        }
    }

    // GET MY PRODUCTS
    public Message handleGetMyProducts(String payload) {
        try {
            GetMyProductsDto dto = gson.fromJson(payload, GetMyProductsDto.class);
            List<Item> items = auctionService.getProductsBySeller(dto.sellerId());
            return new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of("items", items)));
        } catch (Exception e) {
            return error("Không thể tải sản phẩm: " + e.getMessage());
        }
    }

    // ADD PRODUCT
    public Message handleAddProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Chi Seller moi co the them san pham.");
        }
        try {
            AddProductDto dto = gson.fromJson(payload, AddProductDto.class);

            if (dto.name() == null || dto.name().isBlank())
                return error("Ten san pham khong duoc rong.");
            if (dto.startPrice() <= 0)
                return error("Gia khoi diem phai > 0.");
            if (dto.endTime() == null || dto.endTime().isBlank())
                return error("Thoi gian ket thuc khong duoc rong.");

            LocalDateTime endTime;
            try {
                endTime = LocalDateTime.parse(dto.endTime().replace(" ", "T"));
            } catch (Exception e) {
                return error("Dinh dang thoi gian ket thuc khong hop le.");
            }
            if (endTime.isBefore(LocalDateTime.now()))
                return error("Thoi gian ket thuc da qua.");

            LocalDateTime startTime = (dto.startTime() != null && !dto.startTime().isBlank())
                    ? LocalDateTime.parse(dto.startTime().replace(" ", "T"))
                    : LocalDateTime.now();

            Item item = auctionService.addProduct(
                    dto.sellerId(), dto.name(),
                    dto.description() != null ? dto.description() : "",
                    dto.startPrice(),
                    dto.bidIncrement() > 0 ? dto.bidIncrement() : 1000.0,
                    dto.imagePath() != null ? dto.imagePath() : "",
                    startTime, endTime
            );

            if (item == null) return error("Khong the them san pham. Vui long thu lai.");

            return new Message("ADD_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", true, "item", item)));
        } catch (Exception e) {
            e.printStackTrace();
            return error("Loi he thong: " + e.getMessage());
        }
    }

    // UPDATE PRODUCT
    public Message handleUpdateProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Khong co quyen cap nhat san pham.");
        }
        try {
            UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
            if (dto.productId() == null || dto.productId().isBlank())
                return error("productId khong duoc rong.");

            LocalDateTime endTime = LocalDateTime.parse(dto.endTime().replace(" ", "T"));

            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    dto.startPrice(), dto.bidIncrement(), endTime);

            return new Message("UPDATE_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", ok,
                            "message", ok ? "Cap nhat thanh cong." : "Cap nhat that bai.")));
        } catch (Exception e) {
            return error("Loi cap nhat san pham: " + e.getMessage());
        }
    }

    // DELETE PRODUCT
    public Message handleDeleteProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Khong co quyen xoa san pham.");
        }
        try {
            DeleteProductDto dto = gson.fromJson(payload, DeleteProductDto.class);
            boolean ok = auctionService.deleteProduct(dto.productId());
            return new Message("DELETE_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", ok,
                            "message", ok ? "Da xoa san pham." : "Khong the xoa.")));
        } catch (Exception e) {
            return error("Loi xoa san pham: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // PLACE BID
    //
    // FIX QUAN TRỌNG: broadcast BID_UPDATE theo 2 kênh:
    //   1. broadcastToAll()     → tất cả client nhận BID_UPDATE (Home screen cập nhật giá)
    //   2. broadcastToAuction() → chỉ client watching phiên đó nhận TIME_EXTENDED
    //
    // Lý do cần broadcastToAll cho BID_UPDATE:
    //   - HomeController hiển thị danh sách tất cả phiên, cần cập nhật giá realtime
    //   - HomeController không gửi WATCH_AUCTION nên không được nhận từ broadcastToAuction
    //   - DetailController đã gửi WATCH_AUCTION → nhận được từ cả 2 kênh (không sao)
    //   - LiveBiddingController đã gửi WATCH_AUCTION → tương tự
    //   - Việc HomeController nhận BID_UPDATE của phiên khác không gây vấn đề
    //     vì HomeController filter theo productId trong list của mình
    // ─────────────────────────────────────────────────────────────────────
    public void handlePlaceBid(String payload, String role,
                               String bidderId, String bidderName,
                               ClientHandler caller) {
        if (!"BIDDER".equalsIgnoreCase(role)) {
            caller.send(new Message("BID_RESULT",
                    gson.toJson(Map.of("success", false,
                            "message", "Chi Bidder moi co the dat gia."))));
            return;
        }

        PlaceBidDto dto;
        try {
            dto = gson.fromJson(payload, PlaceBidDto.class);
        } catch (Exception e) {
            caller.send(new Message("BID_RESULT",
                    gson.toJson(Map.of("success", false, "message", "Du lieu khong hop le."))));
            return;
        }

        if (dto.productId() == null || dto.amount() <= 0) {
            caller.send(new Message("BID_RESULT",
                    gson.toJson(Map.of("success", false, "message", "Du lieu bid khong hop le."))));
            return;
        }

        AuctionService.BidOutcome outcome =
                auctionService.placeBid(dto.productId(), bidderId, dto.amount());

        boolean success = outcome.result() == AuctionService.BidResult.SUCCESS;

        caller.send(new Message("BID_RESULT",
                gson.toJson(Map.of("success", success, "message", outcome.message()))));

        if (!success) return;

        // Payload BID_UPDATE
        String bidUpdatePayload = gson.toJson(Map.of(
                "productId",  dto.productId(),
                "newBid",     outcome.newBid(),
                "bidderName", bidderName
        ));
        Message bidUpdateMsg = new Message("BID_UPDATE", bidUpdatePayload);

        // FIX: broadcastToAll để Home screen cũng nhận được BID_UPDATE
        // (Home không gửi WATCH_AUCTION nên không nhận từ broadcastToAuction)
        server.broadcastToAll(bidUpdateMsg);

        // TIME_EXTENDED: chỉ gửi cho client đang xem phiên này (không cần gửi cho Home)
        if (outcome.newEndTime() != null) {
            String extPayload = gson.toJson(Map.of(
                    "productId",  dto.productId(),
                    "newEndTime", outcome.newEndTime()
            ));
            server.broadcastToAuction(dto.productId(),
                    new Message("TIME_EXTENDED", extPayload));
            System.out.printf("[AuctionController] Anti-sniping: phien %s gia han -> %s%n",
                    dto.productId(), outcome.newEndTime());
        }

        // Auto-bid
        AutoBidService.AutoBidResult autoBid =
                autoBidService.triggerAutoBid(dto.productId(), outcome.newBid(), bidderId);

        if (autoBid != null) {
            String autoBidPayload = gson.toJson(Map.of(
                    "productId",  dto.productId(),
                    "newBid",     autoBid.newBid(),
                    "bidderName", autoBid.bidderId() + " (auto)"
            ));
            // FIX: broadcastToAll cho auto-bid cũng vậy
            server.broadcastToAll(new Message("BID_UPDATE", autoBidPayload));

            if (autoBid.newEndTime() != null) {
                String extPayload = gson.toJson(Map.of(
                        "productId",  dto.productId(),
                        "newEndTime", autoBid.newEndTime()
                ));
                server.broadcastToAuction(dto.productId(),
                        new Message("TIME_EXTENDED", extPayload));
            }
            System.out.printf("[AuctionController] AutoBid triggered: %s -> %.0f%n",
                    autoBid.bidderId(), autoBid.newBid());
        }
    }

    // GET BID HISTORY — nhận cả key "productId" lẫn "itemId"
    public Message handleGetBidHistory(String payload) {
        try {
            JsonObject json = gson.fromJson(payload, JsonObject.class);

            String productId = null;
            if (json.has("productId") && !json.get("productId").isJsonNull()) {
                productId = json.get("productId").getAsString();
            } else if (json.has("itemId") && !json.get("itemId").isJsonNull()) {
                productId = json.get("itemId").getAsString();
            }

            if (productId == null || productId.isBlank()) {
                return new Message("BID_HISTORY_RESPONSE", "[]");
            }

            List<BidDAO.BidRecord> history = auctionService.getBidHistory(productId);
            return new Message("BID_HISTORY_RESPONSE", gson.toJson(history));

        } catch (Exception e) {
            System.err.println("[AuctionController] handleGetBidHistory lỗi: " + e.getMessage());
            return new Message("BID_HISTORY_RESPONSE", "[]");
        }
    }

    // GET USER BID HISTORY
    public Message handleGetUserBidHistory(String payload) {
        try {
            Map<String, String> requestData = gson.fromJson(payload, Map.class);
            String bidderId = requestData.get("bidderId");

            if (bidderId == null || bidderId.isBlank()) {
                return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
            }

            List<Map<String, Object>> historyRecords = auctionService.getUserBidHistory(bidderId);
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(historyRecords));

        } catch (Exception e) {
            System.err.println("[AuctionController] Lỗi lấy lịch sử user: " + e.getMessage());
            return new Message("USER_BID_HISTORY_RESPONSE", gson.toJson(List.of()));
        }
    }

    // GET PRODUCT HISTORY
    public Message handleGetProductHistory(String payload) {
        try {
            BidderDto dto = gson.fromJson(payload, BidderDto.class);
            List<Map<String, Object>> records =
                    auctionService.getBidHistoryForBidder(dto.bidderId());

            return new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "records", records
            )));
        } catch (Exception e) {
            return error("Lỗi tải lịch sử: " + e.getMessage());
        }
    }

    // REGISTER AUTO-BID
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

    // CANCEL AUTO-BID
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

    // GET PRODUCT DETAIL
    public Message handleGetProductDetail(String payload) {
        try {
            GetProductDetailDto dto = gson.fromJson(payload, GetProductDetailDto.class);
            com.auction.server.dao.item.ItemFindDAO itemFindDAO =
                    new com.auction.server.dao.item.ItemFindDAO();
            Item item = itemFindDAO.findById(dto.itemId());
            if (item == null) {
                return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                        "success", false, "message", "Không tìm thấy sản phẩm")));
            }
            return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                    "success", true, "item", item)));
        } catch (Exception e) {
            return error("Lỗi tải sản phẩm: " + e.getMessage());
        }
    }

    // Helpers
    private Message error(String msg) {
        return new Message("ERROR", gson.toJson(Map.of("message", msg)));
    }

    private Message autoBidResult(boolean success, String message) {
        return new Message("AUTO_BID_RESULT",
                gson.toJson(Map.of("success", success, "message", message)));
    }

    // DTOs nội bộ
    private record GetMyProductsDto(String sellerId) {}
    private record PlaceBidDto(String productId, String bidderId, double amount) {}
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
    private record GetProductDetailDto(String itemId) {}
}
