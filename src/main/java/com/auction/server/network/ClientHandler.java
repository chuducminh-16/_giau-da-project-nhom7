package com.auction.server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.auction.client.model.Product;
import com.auction.client.network.Message;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.service.AuctionService;
import com.auction.server.service.AutoBidService;
import com.auction.server.service.UserService;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

/**
 * ClientHandler — xử lý 1 kết nối client.
 *
 * PATCH thêm:
 *   - GET_BID_HISTORY: gửi lịch sử bid về client khi vào phòng
 *   - REGISTER_AUTO_BID / CANCEL_AUTO_BID: đăng ký / hủy auto-bid
 *   - placeBid: sau khi thành công → trigger AutoBidService
 *   - placeBid: nếu anti-sniping gia hạn → broadcast TIME_EXTENDED
 *   - Fix: phân quyền dùng getRole() thay vì hardcode
 */
public class ClientHandler implements Runnable {

    private final Socket         socket;
    private final NetworkServer  server;
    private final UserService    userService    = new UserService();
    private final AuctionService auctionService = new AuctionService();
    private final AutoBidService autoBidService = new AutoBidService(auctionService);
    private final Gson           gson           = new Gson();

    private PrintWriter    out;
    private BufferedReader in;
    private User           currentUser;
    private String         watchingAuctionId;   // itemId đang theo dõi

    public ClientHandler(Socket socket, NetworkServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);
                System.out.println("[Server] Nhận: " + msg.getType());
                route(msg);
            }
        } catch (IOException e) {
            System.out.println("[ClientHandler] Client ngắt kết nối: " + e.getMessage());
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── Router ──────────────────────────────────────────────────────────
    private void route(Message msg) {
        switch (msg.getType()) {
            case "LOGIN"               -> handleLogin(msg.getPayload());
            case "REGISTER"            -> handleRegister(msg.getPayload());
            case "GET_MY_PRODUCTS"     -> handleGetMyProducts(msg.getPayload());
            case "ADD_PRODUCT"         -> handleAddProduct(msg.getPayload());
            case "UPDATE_PRODUCT"      -> handleUpdateProduct(msg.getPayload());
            case "DELETE_PRODUCT"      -> handleDeleteProduct(msg.getPayload());
            case "GET_AUCTIONS"        -> handleGetAuctions();
            case "WATCH_AUCTION"       -> handleWatchAuction(msg.getPayload());
            case "PLACE_BID"           -> handlePlaceBid(msg.getPayload());
            case "GET_BID_HISTORY"     -> handleGetBidHistory(msg.getPayload());
            case "REGISTER_AUTO_BID"   -> handleRegisterAutoBid(msg.getPayload());
            case "CANCEL_AUTO_BID"     -> handleCancelAutoBid(msg.getPayload());
            default -> send(errorMsg("Unknown type: " + msg.getType()));
        }
    }

    // ═══════════════════════════════════════════════════════
    // LOGIN
    // ═══════════════════════════════════════════════════════
    private void handleLogin(String payload) {
        try {
            LoginDto dto = gson.fromJson(payload, LoginDto.class);
            User user = userService.login(dto.username(), dto.password());

            if (user != null) {
                this.currentUser = user;
                send(new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                        "success",  true,
                        "userId",   user.getId(),
                        "username", user.getUsername(),
                        "email",    user.getEmail() != null ? user.getEmail() : "",
                        "role",     user.getRole()
                ))));
            } else {
                send(new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                        "success", false,
                        "message", "Sai tên đăng nhập hoặc mật khẩu."
                ))));
            }
        } catch (Exception e) {
            e.printStackTrace();
            send(new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi server: " + e.getMessage()
            ))));
        }
    }

    // ═══════════════════════════════════════════════════════
    // REGISTER
    // ═══════════════════════════════════════════════════════
    private void handleRegister(String payload) {
        RegisterDto dto = gson.fromJson(payload, RegisterDto.class);
        UserService.RegisterResult result = userService.register(
                dto.username(), dto.email(), dto.password(),
                dto.fullName(), dto.phone(), dto.address(), dto.role()
        );
        boolean success = result == UserService.RegisterResult.SUCCESS;
        String message = switch (result) {
            case SUCCESS         -> "Đăng ký thành công!";
            case EMAIL_EXISTS    -> "Email đã được sử dụng.";
            case USERNAME_EXISTS -> "Tên đăng nhập đã tồn tại.";
            default              -> "Lỗi hệ thống, thử lại sau.";
        };
        send(new Message("REGISTER_RESPONSE", gson.toJson(Map.of(
                "success", success, "message", message))));
    }

    // ═══════════════════════════════════════════════════════
    // GET MY PRODUCTS
    // ═══════════════════════════════════════════════════════
    private void handleGetMyProducts(String payload) {
        if (!isAuthenticated()) return;
        SellerDto dto = gson.fromJson(payload, SellerDto.class);
        List<Product> products = auctionService.getProductsBySeller(dto.sellerId());
        send(new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success", true, "products", products))));
    }

    // ═══════════════════════════════════════════════════════
    // ADD PRODUCT
    // ═══════════════════════════════════════════════════════
    private void handleAddProduct(String payload) {
        if (!isAuthenticated()) return;
        String role = currentUser.getRole();
        if (!"SELLER".equals(role) && !"ADMIN".equals(role)) {
            send(errorMsg("Bạn không có quyền đăng sản phẩm."));
            return;
        }
        ProductDto dto = gson.fromJson(payload, ProductDto.class);
        try {
            LocalDateTime startTime = dto.startTime() != null
                    ? LocalDateTime.parse(dto.startTime()) : LocalDateTime.now();
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());
            Product created = auctionService.addProduct(
                    dto.sellerId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()),
                    dto.imagePath(), startTime, endTime);
            if (created != null) {
                send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                        "success", true, "message", "Thêm sản phẩm thành công!", "product", created))));
            } else {
                send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                        "success", false, "message", "Không thể lưu sản phẩm."))));
            }
        } catch (Exception e) {
            send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "Lỗi: " + e.getMessage()))));
        }
    }

    // ═══════════════════════════════════════════════════════
    // UPDATE PRODUCT
    // ═══════════════════════════════════════════════════════
    private void handleUpdateProduct(String payload) {
        if (!isAuthenticated()) return;
        UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
        try {
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());
            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()), endTime);
            send(new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", ok,
                    "message", ok ? "Cập nhật thành công!" : "Cập nhật thất bại."))));
        } catch (Exception e) {
            send(new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "Lỗi: " + e.getMessage()))));
        }
    }

    // ═══════════════════════════════════════════════════════
    // DELETE PRODUCT
    // ═══════════════════════════════════════════════════════
    private void handleDeleteProduct(String payload) {
        if (!isAuthenticated()) return;
        DeleteDto dto = gson.fromJson(payload, DeleteDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());
        send(new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xoá sản phẩm!" : "Xoá thất bại."))));
    }

    // ═══════════════════════════════════════════════════════
    // GET AUCTIONS
    // ═══════════════════════════════════════════════════════
    private void handleGetAuctions() {
        List<Product> list = auctionService.getActiveAuctions();
        send(new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success", true, "auctions", list))));
    }

    // ═══════════════════════════════════════════════════════
    // WATCH AUCTION
    // ═══════════════════════════════════════════════════════
    private void handleWatchAuction(String payload) {
        WatchDto dto = gson.fromJson(payload, WatchDto.class);
        this.watchingAuctionId = dto.auctionId();
    }

    // ═══════════════════════════════════════════════════════
    // GET BID HISTORY — MỚI
    // ═══════════════════════════════════════════════════════
    private void handleGetBidHistory(String payload) {
        BidHistoryDto dto = gson.fromJson(payload, BidHistoryDto.class);
        List<BidDAO.BidRecord> history = auctionService.getBidHistory(dto.productId());
        send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                "success", true,
                "productId", dto.productId(),
                "history", history))));
    }

    // ═══════════════════════════════════════════════════════
    // PLACE BID — thêm Anti-sniping broadcast + AutoBid trigger
    // ═══════════════════════════════════════════════════════
    private void handlePlaceBid(String payload) {
        if (!isAuthenticated()) return;

        if (!"BIDDER".equals(currentUser.getRole())) {
            send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false, "message", "Chỉ Bidder mới được đặt giá."))));
            return;
        }

        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);
        AuctionService.BidOutcome outcome = auctionService.placeBid(
                dto.productId(), currentUser.getId(), dto.amount());

        switch (outcome.result()) {
            case SUCCESS -> {
                // Trả kết quả cho bidder
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()))));

                // Broadcast BID_UPDATE cho tất cả client đang xem phiên
                Map<String, Object> bidUpdatePayload = new java.util.HashMap<>(Map.of(
                        "productId",  dto.productId(),
                        "newBid",     outcome.newBid(),
                        "bidderId",   currentUser.getId(),
                        "bidderName", currentUser.getUsername(),
                        "timestamp",  LocalDateTime.now().toString()
                ));
                server.broadcastToAuction(dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(bidUpdatePayload)));

                // Nếu anti-sniping gia hạn → broadcast TIME_EXTENDED
                if (outcome.newEndTime() != null) {
                    server.broadcastToAuction(dto.productId(),
                            new Message("TIME_EXTENDED", gson.toJson(Map.of(
                                    "productId",  dto.productId(),
                                    "newEndTime", outcome.newEndTime(),
                                    "message",    "Phiên được gia hạn thêm 60 giây!"
                            ))));
                }

                // Trigger auto-bid nếu có đăng ký
                AutoBidService.AutoBidResult autoBid = autoBidService.triggerAutoBid(
                        dto.productId(), outcome.newBid(), currentUser.getId());
                if (autoBid != null) {
                    // Broadcast auto-bid thành công
                    Map<String, Object> autoUpdatePayload = new java.util.HashMap<>(Map.of(
                            "productId",  dto.productId(),
                            "newBid",     autoBid.newBid(),
                            "bidderId",   autoBid.bidderId(),
                            "bidderName", "[Auto-Bid] " + autoBid.bidderId(),
                            "timestamp",  LocalDateTime.now().toString()
                    ));
                    server.broadcastToAuction(dto.productId(),
                            new Message("BID_UPDATE", gson.toJson(autoUpdatePayload)));

                    if (autoBid.newEndTime() != null) {
                        server.broadcastToAuction(dto.productId(),
                                new Message("TIME_EXTENDED", gson.toJson(Map.of(
                                        "productId",  dto.productId(),
                                        "newEndTime", autoBid.newEndTime(),
                                        "message",    "Phiên được gia hạn thêm 60 giây!"
                                ))));
                    }
                }
            }
            case PRICE_TOO_LOW ->
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", false,
                        "message", outcome.message(),
                        "currentBid", outcome.newBid()))));
            case AUCTION_ENDED ->
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", false, "message", "Phiên đấu giá đã kết thúc."))));
            case AUCTION_NOT_FOUND ->
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", false, "message", "Sản phẩm không tồn tại."))));
            default ->
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", false, "message", "Lỗi hệ thống."))));
        }
    }

    // ═══════════════════════════════════════════════════════
    // REGISTER AUTO-BID — MỚI
    // ═══════════════════════════════════════════════════════
    private void handleRegisterAutoBid(String payload) {
        if (!isAuthenticated()) return;
        if (!"BIDDER".equals(currentUser.getRole())) {
            send(errorMsg("Chỉ Bidder mới được đăng ký Auto-Bid."));
            return;
        }
        AutoBidDto dto = gson.fromJson(payload, AutoBidDto.class);
        boolean ok = autoBidService.registerAutoBid(
                dto.itemId(), currentUser.getId(), dto.maxBid(), dto.increment());
        send(new Message("AUTO_BID_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã đăng ký Auto-Bid!" : "Lỗi đăng ký Auto-Bid."))));
    }

    // ═══════════════════════════════════════════════════════
    // CANCEL AUTO-BID — MỚI
    // ═══════════════════════════════════════════════════════
    private void handleCancelAutoBid(String payload) {
        if (!isAuthenticated()) return;
        AutoBidCancelDto dto = gson.fromJson(payload, AutoBidCancelDto.class);
        boolean ok = autoBidService.cancelAutoBid(dto.itemId(), currentUser.getId());
        send(new Message("AUTO_BID_CANCEL_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã hủy Auto-Bid." : "Lỗi hủy Auto-Bid."))));
    }

    // ── Helpers ─────────────────────────────────────────────────────────
    public synchronized void send(Message msg) {
        if (out != null) out.println(gson.toJson(msg));
    }

    private boolean isAuthenticated() {
        if (currentUser == null) {
            send(errorMsg("Vui lòng đăng nhập trước."));
            return false;
        }
        return true;
    }

    private Message errorMsg(String text) {
        return new Message("ERROR", gson.toJson(Map.of("message", text)));
    }

    public boolean isWatchingAuction(String auctionId) {
        return auctionId != null && auctionId.equals(watchingAuctionId);
    }

    // ── DTOs ────────────────────────────────────────────────────────────
    private record LoginDto(String username, String password) {}
    private record RegisterDto(String username, String email, String password,
                               String fullName, String phone, String address, String role) {}
    private record SellerDto(String sellerId) {}
    private record ProductDto(String sellerId, String name, String description,
                              String startPrice, String bidIncrement,
                              String imagePath, String startTime, String endTime) {}
    private record UpdateProductDto(String productId, String name, String description,
                                    String startPrice, String bidIncrement, String endTime) {}
    private record DeleteDto(String productId) {}
    private record WatchDto(String auctionId) {}
    private record PlaceBidDto(String productId, double amount) {}
    private record BidHistoryDto(String productId) {}
    private record AutoBidDto(String itemId, double maxBid, double increment) {}
    private record AutoBidCancelDto(String itemId) {}
}