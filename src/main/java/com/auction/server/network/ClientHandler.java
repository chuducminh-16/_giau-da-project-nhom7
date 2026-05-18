package com.auction.server.network;

import com.auction.server.service.AuctionService;
import com.auction.server.service.UserService;
import com.auction.model.Entity.Auction_Bid.Auction;
import com.auction.model.Entity.User.User;
import com.auction.shared.network.Message;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket         socket;
    private final NetworkServer  server;
    private final UserService    userService    = new UserService();
    private final AuctionService auctionService = new AuctionService();
    private final Gson           gson           = new Gson();

    private PrintWriter    out;
    private BufferedReader in;
    private User           currentUser;        // null = chưa login
    private String         watchingAuctionId;  // đang xem phiên nào

    public ClientHandler(Socket socket, NetworkServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);
                System.out.println("[Server] Nhận: " + msg.getType());
                route(msg);
            }
        } catch (IOException e) {
            System.out.println("[ClientHandler] Client ngắt kết nối.");
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── Router ─────────────────────────────────────────
    private void route(Message msg) {
        switch (msg.getType()) {
            case "LOGIN"           -> handleLogin(msg.getPayload());
            case "REGISTER"        -> handleRegister(msg.getPayload());
            case "GET_MY_PRODUCTS" -> handleGetMyProducts(msg.getPayload());
            case "ADD_PRODUCT"     -> handleAddProduct(msg.getPayload());
            case "UPDATE_PRODUCT"  -> handleUpdateProduct(msg.getPayload());
            case "DELETE_PRODUCT"  -> handleDeleteProduct(msg.getPayload());
            case "GET_AUCTIONS"    -> handleGetAuctions();
            case "WATCH_AUCTION"   -> handleWatchAuction(msg.getPayload());
            case "PLACE_BID"       -> handlePlaceBid(msg.getPayload());
            default -> send(new Message("ERROR",
                    gson.toJson(Map.of("message",
                            "Unknown type: " + msg.getType()))));
        }
    }

    // ══════════════════════════════════════════
    // LUỒNG 1: LOGIN
    // ══════════════════════════════════════════
    private void handleLogin(String payload) {
        LoginDto dto = gson.fromJson(payload, LoginDto.class);

        User user = userService.login(dto.email(), dto.password());

        if (user != null) {
            this.currentUser = user;
            send(new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                    "success",  true,
                    "userId",   user.getId(),        // String — từ Entity
                    "username", user.getUsername(),
                    "email",    user.getEmail(),
                    "role",     user.getRole()       // ✅ FIX: bỏ .name() vì getRole() đã là String
            ))));
        } else {
            send(new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Sai email hoặc mật khẩu."
            ))));
        }
    }

    // ══════════════════════════════════════════
    // LUỒNG 2: REGISTER
    // ══════════════════════════════════════════
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
                "success", success,
                "message", message
        ))));
    }

    // ══════════════════════════════════════════
    // LUỒNG 3: MANAGE PRODUCT
    // ══════════════════════════════════════════
    private void handleGetMyProducts(String payload) {
        if (!isAuthenticated()) return;

        SellerDto dto = gson.fromJson(payload, SellerDto.class);
        List<Auction> products = auctionService
                .getProductsBySeller(dto.sellerId());

        send(new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success",  true,
                "products", products
        ))));
    }

    private void handleAddProduct(String payload) {
        if (!isAuthenticated()) return;

        // ✅ FIX: thay canCreateAuction() bằng kiểm tra role trực tiếp
        if (!"SELLER".equals(currentUser.getRole()) &&
                !"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Bạn không có quyền đăng sản phẩm."));
            return;
        }

        ProductDto dto = gson.fromJson(payload, ProductDto.class);

        try {
            LocalDateTime startTime = dto.startTime() != null
                    ? LocalDateTime.parse(dto.startTime())
                    : LocalDateTime.now();
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());

            Auction created = auctionService.addProduct(
                    dto.sellerId(),
                    dto.name(),
                    dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()),
                    dto.imagePath(),
                    startTime,
                    endTime
            );

            if (created != null) {
                send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                        "success", true,
                        "message", "Thêm sản phẩm thành công!",
                        "product", created
                ))));
            } else {
                send(errorMsg("Không thể lưu sản phẩm."));
            }

        } catch (Exception e) {
            send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi: " + e.getMessage()
            ))));
        }
    }

    private void handleUpdateProduct(String payload) {
        if (!isAuthenticated()) return;

        UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);

        try {
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());
            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()),
                    endTime
            );

            send(new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", ok,
                    "message", ok ? "Cập nhật thành công!" : "Cập nhật thất bại."
            ))));

        } catch (Exception e) {
            send(new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi: " + e.getMessage()
            ))));
        }
    }

    private void handleDeleteProduct(String payload) {
        if (!isAuthenticated()) return;

        DeleteDto dto = gson.fromJson(payload, DeleteDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());

        send(new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xoá sản phẩm!" : "Xoá thất bại."
        ))));
    }

    private void handleGetAuctions() {
        List<Auction> list = auctionService.getActiveAuctions();
        send(new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success",  true,
                "auctions", list
        ))));
    }

    private void handleWatchAuction(String payload) {
        WatchDto dto = gson.fromJson(payload, WatchDto.class);
        this.watchingAuctionId = dto.auctionId();
    }

    // ══════════════════════════════════════════
    // LUỒNG 4: PLACE BID
    // ══════════════════════════════════════════
    private void handlePlaceBid(String payload) {

        // 1. Phải đăng nhập mới được bid
        if (!isAuthenticated()) return;

        // ✅ FIX: thay canBid() bằng kiểm tra role trực tiếp
        if (!"BIDDER".equals(currentUser.getRole())) {
            send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false,
                    "message", "Chỉ Bidder mới được đặt giá."
            ))));
            return;
        }

        // 2. Parse payload
        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);

        // 3. Gọi service — có ReentrantLock bên trong
        AuctionService.BidOutcome outcome = auctionService.placeBid(
                dto.productId(),
                currentUser.getId(),   // lấy từ session server, không tin client
                dto.amount()
        );

        // 4. Xử lý kết quả
        switch (outcome.result()) {

            case SUCCESS -> {
                // Trả kết quả cho người vừa bid
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()
                ))));

                // Broadcast BID_UPDATE đến TẤT CẢ client đang xem phiên
                server.broadcastToAuction(
                        dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(Map.of(
                                "productId",  dto.productId(),
                                "newBid",     outcome.newBid(),
                                "bidderId",   currentUser.getId(),
                                "bidderName", currentUser.getUsername(),
                                "timestamp",  LocalDateTime.now().toString()
                        )))
                );
            }

            case PRICE_TOO_LOW -> send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success",    false,
                            "message",    outcome.message(),
                            "currentBid", outcome.newBid()
                    ))));

            case AUCTION_ENDED -> send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success", false,
                            "message", "Phiên đấu giá đã kết thúc."
                    ))));

            case AUCTION_NOT_FOUND -> send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success", false,
                            "message", "Sản phẩm không tồn tại."
                    ))));

            default -> send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success", false,
                            "message", "Lỗi hệ thống."
                    ))));
        }
    }

    // ── Helpers ────────────────────────────────────────
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
        return new Message("ERROR",
                gson.toJson(Map.of("message", text)));
    }

    public boolean isWatchingAuction(String auctionId) {
        return auctionId.equals(watchingAuctionId);
    }

    // ── Inner DTOs ─────────────────────────────────────
    private record LoginDto(
            String email,
            String password) {}

    private record RegisterDto(
            String username, String email, String password,
            String fullName, String phone, String address,
            String role) {}

    private record SellerDto(
            String sellerId) {}

    private record ProductDto(
            String sellerId, String name, String description,
            String startPrice, String bidIncrement,
            String imagePath, String startTime, String endTime) {}

    private record UpdateProductDto(
            String productId, String name, String description,
            String startPrice, String bidIncrement,
            String endTime) {}

    private record DeleteDto(
            String productId) {}

    private record WatchDto(
            String auctionId) {}

    private record PlaceBidDto(
            String productId,
            double amount) {}
}