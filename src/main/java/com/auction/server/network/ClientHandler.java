package com.auction.server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

import com.auction.client.network.Message;
import com.auction.server.controller.AdminController;
import com.auction.server.controller.AuctionRoomEngineController;
import com.auction.server.controller.AutoBidController; // Thêm import Controller mới
import com.auction.server.controller.ProductController; // Thêm import Controller mới
import com.auction.server.controller.UserController;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

/**
 * ClientHandler - Chịu trách nhiệm duy trì kết nối Socket với từng Client,
 * lắng nghe thông điệp tuần tự và định tuyến (route) đến các Controller tương ứng.
 */
public class ClientHandler implements Runnable {

    private final Socket         socket;
    private final NetworkServer  server;
    private final Gson           gson = new Gson();

    // Các Controller điều hướng nghiệp vụ của hệ thống
    private final UserController            userController;
    private final AuctionRoomEngineController auctionController; // Đảm nhận Live-Bidding Realtime
    private final ProductController          productController;  // Ổn định nghiệp vụ CRUD sản phẩm
    private final AutoBidController          autoBidController;  // Quản lý luồng bật/tắt Bot tự động
    private final AdminController            adminController;
    
    // Đối tượng ImageHandler độc lập đảm nhận các tác vụ liên quan tới hình ảnh
    private final ImageHandler      imageHandler;

    private PrintWriter out;
    private BufferedReader in;
    private User         currentUser;       // Lưu thông tin người dùng sau khi login thành công (Session trên RAM Server)
    private String         watchingAuctionId; // ID của phòng đấu giá mà client này đang mở xem

    public ClientHandler(Socket socket, NetworkServer server) {
        this.socket            = socket;
        this.server            = server;
        this.userController    = new UserController();
        this.auctionController = new AuctionRoomEngineController(server);
        this.productController = new ProductController(); // Khởi tạo bộ quản lý sản phẩm
        this.autoBidController = new AutoBidController(); // Khởi tạo bộ quản lý Bot
        this.adminController   = new AdminController(server);
        this.imageHandler      = new ImageHandler(this.gson); // Khởi tạo bộ xử lý ảnh chuyên biệt
    }

    /**
     * Vòng lặp Socket chạy luồng riêng biệt để liên tục đọc dữ liệu gửi lên từ Client
     */
    @Override
    public void run() {
        try {
            // Khởi tạo kênh xuất (out) để gửi dữ liệu và kênh nhập (in) để đọc dữ liệu qua TCP Stream
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            
            // Đọc liên tục từng dòng luồng JSON cho đến khi client ngắt kết nối
            while ((line = in.readLine()) != null) {
                // Giải mã chuỗi JSON nhận được thành đối tượng cấu trúc Message chung
                Message msg = gson.fromJson(line, Message.class);
                System.out.println("[Server] Nhan: " + msg.getType());
                
                // Định tuyến xử lý thông điệp dựa trên trường 'type'
                route(msg);
            }
        } catch (IOException e) {
            // Lỗi xảy ra khi Client tắt đột ngột hoặc mất mạng (Connection reset)
            System.out.println("[ClientHandler] Client ngat ket noi.");
        } finally {
            // Khi client thoát hoặc mất kết nối, dọn dẹp tài nguyên và xóa khỏi danh sách quản lý chung của Server
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * Hàm route() - Trái tim định tuyến của ClientHandler, phân loại các Message Type
     */
    private void route(Message msg) {
        // Lấy cục chuỗi JSON chi tiết (payload) bên trong gói tin để chuẩn bị phân tích
        String p = msg.getPayload();

        switch (msg.getType()) {

            // ── Xác thực & Tài khoản (Auth) ─────────────────────────────────
            case "LOGIN" -> {
                UserController.LoginResult r = userController.handleLogin(p);
                // Nếu đăng nhập thành công, lưu lại thực thể User vào biến tạm của Handler này để chứng thực session
                if (r.user() != null) currentUser = r.user(); 
                send(r.response());
            }
            case "REGISTER" -> send(userController.handleRegister(p));

            // 🔥 TÍCH HỢP MỚI: Xử lý cập nhật thông tin hồ sơ cá nhân (Profile) 🔥
            case "UPDATE_PROFILE" -> {
                // Bước 1: Bảo mật - Kiểm tra xem Client này đã đăng nhập vào hệ thống chưa
                if (!isAuthenticated()) return;
                
                // Bước 2: Chuyển payload JSON qua cho UserController xử lý cập nhật xuống Database
                Message responseMsg = userController.handleUpdateProfile(p);
                
                // Bước 3: Nếu UserController thông báo cập nhật Database thành công rực rỡ
                if ("UPDATE_PROFILE_RESPONSE".equals(responseMsg.getType())) {
                    // Trích xuất dữ liệu mới từ payload gửi lên để ghi đè (đồng bộ) trực tiếp vào RAM Server
                    Map<String, Object> data = gson.fromJson(p, Map.class);
                    if (data.containsKey("fullName")) currentUser.setFullName((String) data.get("fullName"));
                    if (data.containsKey("email"))    currentUser.setEmail((String) data.get("email"));
                    if (data.containsKey("phone"))    currentUser.setPhone((String) data.get("phone"));
                    if (data.containsKey("address"))  currentUser.setAddress((String) data.get("address"));
                    System.out.println("[ClientHandler] Dong bo RAM Server thanh cong cho: " + currentUser.getUsername());
                }
                
                // Bước 4: Bắn gói tin phản hồi ngược về mạng, Client đang treo chữ "Đang lưu..." sẽ nhận được để mở khóa UI
                send(responseMsg);
            }

            // ── Nghiệp vụ phòng đấu giá thời gian thực (Engine Room) ────────
            case "GET_AUCTIONS" -> send(auctionController.handleGetAuctions());

            case "WATCH_AUCTION" -> {
                // Đánh dấu phòng đấu giá Client đang xem để Server tiện gửi thông báo realtime khi có cập nhật giá mới
                WatchDto dto = gson.fromJson(p, WatchDto.class);
                this.watchingAuctionId = dto.auctionId();
            }
            case "PLACE_BID" -> {
                if (!isAuthenticated()) return;
                auctionController.handlePlaceBid(
                        p, role(), currentUser.getId(),
                        currentUser.getUsername(), this);
            }
            case "GET_BID_HISTORY" -> {
                send(auctionController.handleGetBidHistory(p));
            }
            case "GET_USER_BID_HISTORY" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleGetUserBidHistory(p));
            }
            case "GET_PRODUCT_HISTORY" -> { // Đồng bộ thêm case lịch sử thắng cược cũ của tài khoản nếu có gọi
                send(auctionController.handleGetProductHistory(p));
            }

            // ── Nghiệp vụ Quản lý Sản phẩm (Tuyến ProductController mới) ──
            case "GET_MY_PRODUCTS" -> {
                if (!isAuthenticated()) return;
                send(productController.handleGetMyProducts(p)); // Đã chuyển sang productController
            }
            case "ADD_PRODUCT" -> {
                if (!isAuthenticated()) return;
                send(productController.handleAddProduct(p, role())); // Đã chuyển sang productController
            }
            case "UPDATE_PRODUCT" -> {
                if (!isAuthenticated()) return;
                send(productController.handleUpdateProduct(p, role())); // Đã chuyển sang productController
            }
            case "DELETE_PRODUCT" -> {
                if (!isAuthenticated()) return;
                send(productController.handleDeleteProduct(p, role())); // Đã chuyển sang productController
            }
            case "GET_PRODUCT_DETAIL" -> {
                send(productController.handleGetProductDetail(p)); // Đã chuyển sang productController
            }

            // ── Quản lý tính năng Tự động đặt giá (Tuyến AutoBidController mới) ──
            case "REGISTER_AUTO_BID" -> {
                if (!isAuthenticated()) return;
                send(autoBidController.handleRegisterAutoBid(p, currentUser.getId())); // Đã chuyển sang autoBidController
            }
            case "CANCEL_AUTO_BID" -> {
                if (!isAuthenticated()) return;
                send(autoBidController.handleCancelAutoBid(p, currentUser.getId())); // Đã chuyển sang autoBidController
            }
            
            // ── Tách Logic sang ImageHandler xử lý dữ liệu ảnh hình hình ─────────
            case "UPLOAD_IMAGE" -> {
                if (!isAuthenticated()) return;
                send(imageHandler.handleUploadImage(p));
            }
            case "GET_IMAGE" -> {
                send(imageHandler.handleGetImage(p));
            }

            // ── Khu vực dành riêng cho Quản trị viên (Admin) ─────────────────
            case "ADMIN_GET_PRODUCTS" -> {
                if (!isAdmin()) return;
                send(adminController.handleGetProducts());
            }
            case "ADMIN_GET_USERS" -> {
                if (!isAdmin()) return;
                send(adminController.handleGetUsers());
            }
            case "ADMIN_GET_AUCTIONS" -> {
                if (!isAdmin()) return;
                send(adminController.handleGetAuctions());
            }
            case "ADMIN_DELETE_PRODUCT" -> {
                if (!isAdmin()) return;
                send(adminController.handleDeleteProduct(p));
            }
            case "ADMIN_DELETE_USER" -> {
                if (!isAdmin()) return;
                send(adminController.handleDeleteUser(p, currentUser.getId()));
            }
            case "ADMIN_FORCE_CLOSE_AUCTION" -> {
                if (!isAdmin()) return;
                send(adminController.handleForceCloseAuction(p));
            }

            // Trường hợp nhận Message Type lạ không hợp lệ (Tránh kẹt luồng Client)
            default -> send(new Message("ERROR",
                    gson.toJson(Map.of("message", "Unknown type: " + msg.getType()))));
        }
    }

    /**
     * Gửi dữ liệu đồng bộ (Thread-safe) về phía client qua cổng Output Stream
     */
    public synchronized void send(Message msg) {
        if (out != null) out.println(gson.toJson(msg));
    }

    /**
     * Kiểm tra người dùng hiện tại đã đăng nhập vào hệ thống chưa
     */
    private boolean isAuthenticated() {
        if (currentUser == null) {
            send(new Message("ERROR", gson.toJson(
                    Map.of("message", "Vui lòng đăng nhập trước khi thực hiện thao tác này."))));
            return false;
        }
        return true;
    }

    /**
     * Kiểm tra người dùng hiện tại có phải vai trò ADMIN không
     */
    private boolean isAdmin() {
        if (!isAuthenticated()) return false;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(new Message("ERROR", gson.toJson(
                    Map.of("message", "Từ chối truy cập: Bạn không có quyền quản trị viên."))));
            return false;
        }
        return true;
    }

    /**
     * Lấy chuỗi phân quyền (Role) hiện tại
     */
    private String role() {
        return currentUser != null ? currentUser.getRole() : "";
    }

    /**
     * Kiểm tra xem Client này có đang bật màn hình theo dõi phòng đấu giá này không
     */
    public boolean isWatchingAuction(String auctionId) {
        return auctionId != null && auctionId.equals(watchingAuctionId);
    }

    // Record bổ trợ để parse nhanh JSON xem phòng đấu giá
    private record WatchDto(String auctionId) {}
}