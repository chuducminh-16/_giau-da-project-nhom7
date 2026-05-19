package com.auction.server.network;

import com.auction.server.service.AuctionService;
import com.auction.server.service.UserService;
import com.auction.model.Entity.Auction_Bid.Auction;
import com.auction.model.Entity.User.User;
import com.auction.shared.network.Message;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
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
    private User           currentUser;       // null = chưa đăng nhập
    private String         watchingAuctionId; // ID phiên đang theo dõi, null = không xem

    public ClientHandler(Socket socket, NetworkServer server) {
        this.socket = socket;
        this.server = server;
    }

    // ── Vòng lặp chính: đọc từng dòng JSON từ client ──
    @Override
    public void run() {
        try {
            // FIX: thêm StandardCharsets.UTF_8 để đồng nhất encoding
            // giữa các hệ điều hành, tránh vỡ tiếng Việt
            out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(),
                            StandardCharsets.UTF_8),
                    true); // autoFlush = true: tự flush sau mỗi println()

            in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(),
                            StandardCharsets.UTF_8));

            String line;
            while ((line = in.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);
                System.out.println("[ClientHandler] Nhận: " + msg.getType());
                route(msg);
            }

        } catch (IOException e) {
            System.out.println("[ClientHandler] Client ngắt kết nối: "
                    + e.getMessage());
        } finally {
            // Luôn dọn dẹp kể cả khi có exception
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // ── Router: phân loại message và gọi handler tương ứng ──
    private void route(Message msg) {
        switch (msg.getType()) {
            case "LOGIN"            -> handleLogin(msg.getPayload());
            case "REGISTER"         -> handleRegister(msg.getPayload());
            case "GET_MY_PRODUCTS"  -> handleGetMyProducts(msg.getPayload());
            case "ADD_PRODUCT"      -> handleAddProduct(msg.getPayload());
            case "UPDATE_PRODUCT"   -> handleUpdateProduct(msg.getPayload());
            case "DELETE_PRODUCT"   -> handleDeleteProduct(msg.getPayload());
            case "GET_AUCTIONS"     -> handleGetAuctions();
            case "WATCH_AUCTION"    -> handleWatchAuction(msg.getPayload());
            case "UNWATCH_AUCTION"  -> handleUnwatchAuction(); // FIX: thêm case mới
            case "PLACE_BID"        -> handlePlaceBid(msg.getPayload());
            // FIX: thêm dấu ; và dùng new Message() thay vì Message.error()
            default -> send(new Message("ERROR",
                    gson.toJson(Map.of("message",
                            "Lệnh không hợp lệ: " + msg.getType()))));
        }
    }

    // ══════════════════════════════════════════════════
    // LUỒNG 1: LOGIN
    // ══════════════════════════════════════════════════
    private void handleLogin(String payload) {
        LoginDto dto = gson.fromJson(payload, LoginDto.class);
        User user = userService.login(dto.email(), dto.password());

        if (user != null) {
            this.currentUser = user;
            send(new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                    "success",  true,
                    "userId",   user.getId(),
                    "username", user.getUsername(),
                    "email",    user.getEmail(),
                    "role",     user.getRole()
            ))));
        } else {
            send(new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Sai email hoặc mật khẩu."
            ))));
        }
    }

    // ══════════════════════════════════════════════════
    // LUỒNG 2: REGISTER
    // ══════════════════════════════════════════════════
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

    // ══════════════════════════════════════════════════
    // LUỒNG 3: QUẢN LÝ SẢN PHẨM (dành cho Seller)
    // ══════════════════════════════════════════════════
    private void handleGetMyProducts(String payload) {
        if (!isAuthenticated()) return;

        SellerDto dto = gson.fromJson(payload, SellerDto.class);
        List<Auction> products = auctionService.getProductsBySeller(dto.sellerId());

        send(new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success",  true,
                "products", products
        ))));
    }

    private void handleAddProduct(String payload) {
        if (!isAuthenticated()) return;

        if (!"SELLER".equals(currentUser.getRole())
                && !"ADMIN".equals(currentUser.getRole())) {
            send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Bạn không có quyền đăng sản phẩm."
            ))));
            return;
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
                send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                        "success", true,
                        "message", "Thêm sản phẩm thành công!",
                        "product", created
                ))));
            } else {
                send(new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of(
                        "success", false,
                        "message", "Không thể lưu sản phẩm."
                ))));
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

        // Kiểm tra quyền: chỉ SELLER và ADMIN mới được sửa
        if (!"SELLER".equals(currentUser.getRole())
                && !"ADMIN".equals(currentUser.getRole())) {
            send(new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Bạn không có quyền sửa sản phẩm."
            ))));
            return;
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

        // Kiểm tra quyền: chỉ SELLER và ADMIN mới được xóa
        if (!"SELLER".equals(currentUser.getRole())
                && !"ADMIN".equals(currentUser.getRole())) {
            send(new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Bạn không có quyền xóa sản phẩm."
            ))));
            return;
        }

        DeleteDto dto = gson.fromJson(payload, DeleteDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());

        send(new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xoá sản phẩm!" : "Xoá thất bại."
        ))));
    }

    // ══════════════════════════════════════════════════
    // LUỒNG 4: XEM DANH SÁCH ĐẤU GIÁ
    // ══════════════════════════════════════════════════
    private void handleGetAuctions() {
        // Danh sách công khai — không cần đăng nhập cũng xem được
        List<Auction> list = auctionService.getActiveAuctions();
        send(new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success",  true,
                "auctions", list
        ))));
    }

    // ══════════════════════════════════════════════════
    // LUỒNG 5: WATCH / UNWATCH AUCTION (Observer)
    // ══════════════════════════════════════════════════
    private void handleWatchAuction(String payload) {
        // Không bắt buộc login vì danh sách công khai,
        // nhưng nếu muốn bắt login thì thêm: if (!isAuthenticated()) return;

        WatchDto dto = gson.fromJson(payload, WatchDto.class);
        this.watchingAuctionId = dto.auctionId();

        System.out.println("[ClientHandler] Đang theo dõi phiên: " + watchingAuctionId);

        // FIX: gửi phản hồi về để client biết watch thành công
        send(new Message("WATCH_RESPONSE", gson.toJson(Map.of(
                "success",   true,
                "auctionId", dto.auctionId(),
                "message",   "Đang theo dõi phiên đấu giá."
        ))));
    }

    // FIX: thêm method mới — client gọi khi rời màn hình đấu giá
    private void handleUnwatchAuction() {
        String previous = this.watchingAuctionId;
        this.watchingAuctionId = null; // không còn theo dõi phiên nào

        send(new Message("UNWATCH_RESPONSE", gson.toJson(Map.of(
                "success",   true,
                "auctionId", previous != null ? previous : "",
                "message",   "Đã dừng theo dõi phiên đấu giá."
        ))));
    }

    // ══════════════════════════════════════════════════
    // LUỒNG 6: PLACE BID
    // ══════════════════════════════════════════════════
    private void handlePlaceBid(String payload) {
        // 1. Phải đăng nhập
        if (!isAuthenticated()) return;

        // 2. Chỉ BIDDER mới được đặt giá
        if (!"BIDDER".equals(currentUser.getRole())) {
            send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false,
                    "message", "Chỉ Bidder mới được đặt giá."
            ))));
            return;
        }

        // 3. Parse payload
        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);

        // 4. Gọi service — ReentrantLock nằm bên trong AuctionService
        AuctionService.BidOutcome outcome = auctionService.placeBid(
                dto.productId(),
                currentUser.getId(), // lấy từ session server, không tin client gửi lên
                dto.amount()
        );

        // 5. Xử lý kết quả và phản hồi
        switch (outcome.result()) {

            case SUCCESS -> {
                // Phản hồi riêng cho người vừa đặt giá
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true,
                        "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()
                ))));

                // Broadcast BID_UPDATE đến TẤT CẢ client đang xem phiên này
                // Đây là phần Observer pattern qua socket
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
                            "message", "Phiên đấu giá không tồn tại."
                    ))));

            default -> send(new Message("BID_RESULT",
                    gson.toJson(Map.of(
                            "success", false,
                            "message", "Lỗi hệ thống, thử lại sau."
                    ))));
        }
    }

    // ── Helpers ────────────────────────────────────────
    // synchronized: tránh 2 thread cùng ghi vào socket cùng lúc
    public synchronized void send(Message msg) {
        if (out != null) {
            out.println(gson.toJson(msg));
        }
    }

    // Kiểm tra đăng nhập, tự gửi lỗi nếu chưa login
    private boolean isAuthenticated() {
        if (currentUser == null) {
            send(new Message("ERROR", gson.toJson(Map.of(
                    "message", "Vui lòng đăng nhập trước."
            ))));
            return false;
        }
        return true;
    }

    // Dùng bởi NetworkServer.broadcastToAuction() để lọc client
    public boolean isWatchingAuction(String auctionId) {
        return auctionId != null && auctionId.equals(watchingAuctionId);
    }

    // ── Inner DTOs (record để parse JSON gọn) ──────────
    private record LoginDto(String email, String password) {}

    private record RegisterDto(
            String username, String email, String password,
            String fullName, String phone, String address,
            String role) {}

    private record SellerDto(String sellerId) {}

    private record ProductDto(
            String sellerId, String name, String description,
            String startPrice, String bidIncrement,
            String imagePath, String startTime, String endTime) {}

    private record UpdateProductDto(
            String productId, String name, String description,
            String startPrice, String bidIncrement,
            String endTime) {}

    private record DeleteDto(String productId) {}

    private record WatchDto(String auctionId) {}

    private record PlaceBidDto(String productId, double amount) {}
}