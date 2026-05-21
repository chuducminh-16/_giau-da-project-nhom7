package com.auction.server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.auction.client.network.Message;
import com.auction.server.service.AuctionService;
import com.auction.server.service.UserService;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;
import com.auction.server.dao.item.ItemListDAO;
import com.auction.server.dao.user.UserListDAO;
import com.auction.server.dao.auction.AuctionDAO;

public class ClientHandler implements Runnable {

    private final Socket         socket;
    private final NetworkServer  server;
    private final UserService    userService    = new UserService();
    private final AuctionService auctionService = new AuctionService();
    private final Gson           gson           = new Gson();

    private PrintWriter    out;
    private BufferedReader in;
    private User           currentUser;
    private String         watchingAuctionId;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final ItemListDAO itemListDAO = new ItemListDAO();
    private final UserListDAO userListDAO = new UserListDAO();

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
            case "ADMIN_GET_PRODUCTS"        -> handleAdminGetProducts();
            case "ADMIN_GET_USERS"           -> handleAdminGetUsers();
            case "ADMIN_GET_AUCTIONS"        -> handleAdminGetAuctions();
            case "ADMIN_DELETE_PRODUCT"      -> handleAdminDeleteProduct(msg.getPayload());
            case "ADMIN_DELETE_USER"         -> handleAdminDeleteUser(msg.getPayload());
            case "ADMIN_FORCE_CLOSE_AUCTION" -> handleAdminForceCloseAuction(msg.getPayload());

            default -> send(new Message("ERROR",
                    gson.toJson(Map.of("message", "Unknown type: " + msg.getType()))));
        }
    }

    // ── LOGIN ──────────────────────────────────────────
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

    // ── REGISTER ───────────────────────────────────────
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
                "success", success, "message", message
        ))));
    }

    // ── GET MY PRODUCTS ────────────────────────────────
    private void handleGetMyProducts(String payload) {
        if (!isAuthenticated()) return;
        SellerDto dto = gson.fromJson(payload, SellerDto.class);
        List<Item> items = auctionService.getProductsBySeller(dto.sellerId());
        send(new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                "success", true, "products", items
        ))));
    }

    // ── ADD PRODUCT ────────────────────────────────────
    private void handleAddProduct(String payload) {
        if (!isAuthenticated()) return;
        if (!"SELLER".equals(currentUser.getRole()) && !"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Bạn không có quyền đăng sản phẩm."));
            return;
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
                    "success", false, "message", "Lỗi: " + e.getMessage()
            ))));
        }
    }

    // ── UPDATE PRODUCT ─────────────────────────────────
    private void handleUpdateProduct(String payload) {
        if (!isAuthenticated()) return;
        UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
        try {
            LocalDateTime endTime = LocalDateTime.parse(dto.endTime());
            boolean ok = auctionService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    Double.parseDouble(dto.startPrice()),
                    Double.parseDouble(dto.bidIncrement()), endTime
            );
            send(new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", ok,
                    "message", ok ? "Cập nhật thành công!" : "Cập nhật thất bại."
            ))));
        } catch (Exception e) {
            send(new Message("UPDATE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "Lỗi: " + e.getMessage()
            ))));
        }
    }

    // ── DELETE PRODUCT ─────────────────────────────────
    private void handleDeleteProduct(String payload) {
        if (!isAuthenticated()) return;
        DeleteDto dto = gson.fromJson(payload, DeleteDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());
        send(new Message("DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xoá sản phẩm!" : "Xoá thất bại."
        ))));
    }

    // ── GET AUCTIONS ───────────────────────────────────
    private void handleGetAuctions() {
        List<Item> list = auctionService.getActiveAuctions();
        send(new Message("AUCTIONS_LIST", gson.toJson(Map.of(
                "success", true, "auctions", list
        ))));
    }

    // ── WATCH AUCTION ──────────────────────────────────
    private void handleWatchAuction(String payload) {
        WatchDto dto = gson.fromJson(payload, WatchDto.class);
        this.watchingAuctionId = dto.auctionId();
    }

    // ── PLACE BID ──────────────────────────────────────
    private void handlePlaceBid(String payload) {
        if (!isAuthenticated()) return;
        if (!"BIDDER".equals(currentUser.getRole())) {
            send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false, "message", "Chỉ Bidder mới được đặt giá."
            ))));
            return;
        }
        PlaceBidDto dto = gson.fromJson(payload, PlaceBidDto.class);
        AuctionService.BidOutcome outcome = auctionService.placeBid(
                dto.productId(), currentUser.getId(), dto.amount()
        );
        switch (outcome.result()) {
            case SUCCESS -> {
                send(new Message("BID_RESULT", gson.toJson(Map.of(
                        "success", true, "message", "Đặt giá thành công!",
                        "newBid",  outcome.newBid()
                ))));
                server.broadcastToAuction(dto.productId(),
                        new Message("BID_UPDATE", gson.toJson(Map.of(
                                "productId",  dto.productId(),
                                "newBid",     outcome.newBid(),
                                "bidderId",   currentUser.getId(),
                                "bidderName", currentUser.getUsername(),
                                "timestamp",  LocalDateTime.now().toString()
                        ))));
            }
            case PRICE_TOO_LOW -> send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false, "message", outcome.message(),
                    "currentBid", outcome.newBid()
            ))));
            case AUCTION_ENDED -> send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false, "message", "Phiên đấu giá đã kết thúc."
            ))));
            case AUCTION_NOT_FOUND -> send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false, "message", "Sản phẩm không tồn tại."
            ))));
            default -> send(new Message("BID_RESULT", gson.toJson(Map.of(
                    "success", false, "message", "Lỗi hệ thống."
            ))));
        }
    }

    // ── ADMIN: GET ALL PRODUCTS ────────────────────────────────────────────
    private void handleAdminGetProducts() {
        if (!isAuthenticated()) return;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Không có quyền truy cập."));
            return;
        }
        // Dùng ItemListDAO để lấy toàn bộ sản phẩm
        List<Item> items = auctionService.getActiveAuctions();
        // Lấy tất cả (kể cả FINISHED) bằng query riêng
        String sql = "SELECT * FROM items ORDER BY end_time DESC";
        try (java.sql.Connection conn =
                     com.auction.server.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {

            java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id",            rs.getString("id"));
                row.put("name",          rs.getString("name"));
                row.put("type",          rs.getString("type"));
                row.put("current_price", rs.getDouble("current_price"));
                row.put("seller_id",     rs.getString("seller_id"));
                row.put("status",        rs.getString("status"));
                row.put("end_time",      rs.getString("end_time"));
                rows.add(row);
            }
            send(new Message("ADMIN_PRODUCTS_RESPONSE", gson.toJson(java.util.Map.of(
                    "success", true,
                    "data", rows
            ))));
        } catch (Exception e) {
            e.printStackTrace();
            send(new Message("ADMIN_PRODUCTS_RESPONSE", gson.toJson(java.util.Map.of(
                    "success", false, "message", "Lỗi tải sản phẩm: " + e.getMessage()
            ))));
        }
    }

    // ── ADMIN: GET ALL USERS ───────────────────────────────────────────────
    private void handleAdminGetUsers() {
        if (!isAuthenticated()) return;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Không có quyền truy cập."));
            return;
        }
        String sql = "SELECT id, username, email, role, balance FROM users ORDER BY role, username";
        try (java.sql.Connection conn =
                     com.auction.server.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {

            java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id",       rs.getString("id"));
                row.put("username", rs.getString("username"));
                row.put("email",    rs.getString("email"));
                row.put("role",     rs.getString("role"));
                row.put("balance",  rs.getDouble("balance"));
                rows.add(row);
            }
            send(new Message("ADMIN_USERS_RESPONSE", gson.toJson(java.util.Map.of(
                    "success", true,
                    "data", rows
            ))));
        } catch (Exception e) {
            e.printStackTrace();
            send(new Message("ADMIN_USERS_RESPONSE", gson.toJson(java.util.Map.of(
                    "success", false, "message", "Lỗi tải users: " + e.getMessage()
            ))));
        }
    }

    // ── ADMIN: GET ALL AUCTIONS ────────────────────────────────────────────
    private void handleAdminGetAuctions() {
        if (!isAuthenticated()) return;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Không có quyền truy cập."));
            return;
        }
        String sql = "SELECT a.id, a.item_id, i.name as item_name, a.seller_id, " +
                "a.current_price, a.status, a.end_time " +
                "FROM auctions a LEFT JOIN items i ON a.item_id = i.id " +
                "ORDER BY a.end_time DESC";
        try (java.sql.Connection conn =
                     com.auction.server.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {

            java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("id",           rs.getLong("id"));
                row.put("itemId",       rs.getString("item_id"));
                row.put("itemName",     rs.getString("item_name"));
                row.put("sellerId",     rs.getString("seller_id"));
                row.put("currentPrice", rs.getDouble("current_price"));
                row.put("status",       rs.getString("status"));
                row.put("endTime",      rs.getString("end_time"));
                rows.add(row);
            }
            send(new Message("ADMIN_AUCTIONS_RESPONSE", gson.toJson(java.util.Map.of(
                    "success", true,
                    "data", rows
            ))));
        } catch (Exception e) {
            e.printStackTrace();
            send(new Message("ADMIN_AUCTIONS_RESPONSE", gson.toJson(java.util.Map.of(
                    "success", false, "message", "Lỗi tải phiên: " + e.getMessage()
            ))));
        }
    }

    // ── ADMIN: DELETE PRODUCT ──────────────────────────────────────────────
    private void handleAdminDeleteProduct(String payload) {
        if (!isAuthenticated()) return;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Không có quyền xóa sản phẩm."));
            return;
        }
        AdminProductDto dto = gson.fromJson(payload, AdminProductDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());
        send(new Message("ADMIN_DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xóa sản phẩm!" : "Xóa thất bại."
        ))));
    }

    // ── ADMIN: DELETE USER ─────────────────────────────────────────────────
    private void handleAdminDeleteUser(String payload) {
        if (!isAuthenticated()) return;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Không có quyền xóa tài khoản."));
            return;
        }
        AdminUserDto dto = gson.fromJson(payload, AdminUserDto.class);

        // Không cho xóa chính mình
        if (currentUser.getId().equals(dto.userId())) {
            send(new Message("ADMIN_DELETE_USER_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "Không thể xóa tài khoản đang đăng nhập."
            ))));
            return;
        }

        String sql = "DELETE FROM users WHERE id = ?";
        try (java.sql.Connection conn =
                     com.auction.server.database.DatabaseConnection.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, dto.userId());
            boolean ok = ps.executeUpdate() > 0;
            send(new Message("ADMIN_DELETE_USER_RESPONSE", gson.toJson(Map.of(
                    "success", ok,
                    "message", ok ? "Đã xóa tài khoản!" : "Không tìm thấy tài khoản."
            ))));
        } catch (Exception e) {
            e.printStackTrace();
            send(new Message("ADMIN_DELETE_USER_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "Lỗi: " + e.getMessage()
            ))));
        }
    }

    // ── ADMIN: FORCE CLOSE AUCTION ────────────────────────────────────────
    private void handleAdminForceCloseAuction(String payload) {
        if (!isAuthenticated()) return;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(errorMsg("Không có quyền đóng phiên."));
            return;
        }
        AdminAuctionDto dto = gson.fromJson(payload, AdminAuctionDto.class);
        long auctionId;
        try { auctionId = Long.parseLong(dto.auctionId()); }
        catch (Exception e) {
            send(new Message("ADMIN_CLOSE_AUCTION_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "ID phiên không hợp lệ."))));
            return;
        }

        Map<String, Object> winner = auctionService.endAuction(auctionId);
        boolean ok = true; // endAuction tự xử lý cả CANCELED
        String resultMsg = winner != null
                ? "Đã đóng phiên! Winner: " + winner.get("username")
                + " — " + String.format("%.0f VNĐ", winner.get("finalPrice"))
                : "Đã đóng phiên (không có bid nào, đã hủy).";

        send(new Message("ADMIN_CLOSE_AUCTION_RESPONSE", gson.toJson(Map.of(
                "success", true,
                "message", resultMsg
        ))));

        // Broadcast AUCTION_ENDED cho client đang xem phiên
        if (winner != null) {
            String itemId = (winner.get("bidderId") != null) ? dto.auctionId() : "";
            server.broadcastToAuction(dto.auctionId(),
                    new Message("AUCTION_ENDED", gson.toJson(Map.of(
                            "auctionId",  auctionId,
                            "message",    resultMsg
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
        return new Message("ERROR", gson.toJson(Map.of("message", text)));
    }

    public boolean isWatchingAuction(String auctionId) {
        return auctionId.equals(watchingAuctionId);
    }

    // ── DTOs ───────────────────────────────────────────
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
    private record AdminProductDto(String productId) {}
    private record AdminUserDto(String userId) {}
    private record AdminAuctionDto(String auctionId) {}
}