package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AdminService;
// SỬA ĐỔI: Import dịch vụ sản phẩm mới thay thế cho AuctionService cũ đã phân rã
import com.auction.server.service.auction.AuctionProductService;
import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

/**
 * =========================================================================
 * THƯ MỤC: com.auction.server.controller
 * CLASS: AdminController
 * CHỨC NĂNG CỐT LÕI:
 * - Tiếp nhận mọi yêu cầu điều khiển hệ thống từ tài khoản Quản trị viên (Admin).
 * - Phân rã gói tin mạng (Payload), gọi các dịch vụ xử lý dữ liệu tương ứng,
 * sau đó đóng gói kết quả thành đối tượng Message để phản hồi về Client qua Socket.
 * =========================================================================
 */
public class AdminController {

    private final AdminService adminService = new AdminService();
    
    // SỬA ĐỔI: Thay thế AuctionService cũ bằng dịch vụ đơn nhiệm chuyên biệt quản lý vòng đời sản phẩm
    private final AuctionProductService auctionProductService = new AuctionProductService();
    
    private final NetworkServer server; // Đối tượng điều khiển hạ tầng mạng Socket
    private final Gson gson = new Gson(); // Công cụ chuyển đổi Object sang JSON và ngược lại

    /**
     * Constructor bắt buộc nhận vào NetworkServer để hỗ trợ tính năng 
     * phát sóng (Broadcast) các sự kiện khẩn cấp từ Admin ra toàn sàn.
     */
    public AdminController(NetworkServer server) {
        this.server = server;
    }

    // ── LẤY DANH SÁCH TẤT CẢ SẢN PHẨM ───────────────────────────
    public Message handleGetProducts() {
        try {
            // Gọi AdminService truy vấn dữ liệu thô từ database
            List<Map<String, Object>> rows = adminService.getAllProducts();
            
            // Đóng gói mảng dữ liệu thành chuỗi JSON và trả về thông điệp thành công
            return new Message("ADMIN_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "data",    rows
            )));
        } catch (Exception e) {
            // Trả về gói tin lỗi nếu database gặp sự cố nghẽn hoặc mất kết nối
            return error("ADMIN_PRODUCTS_RESPONSE", "Lỗi tải sản phẩm: " + e.getMessage());
        }
    }

    // ── LẤY DANH SÁCH TẤT CẢ NGƯỜI DÙNG ──────────────────────────────
    public Message handleGetUsers() {
        try {
            List<Map<String, Object>> rows = adminService.getAllUsers();
            return new Message("ADMIN_USERS_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "data",    rows
            )));
        } catch (Exception e) {
            return error("ADMIN_USERS_RESPONSE", "Lỗi tải danh sách người dùng: " + e.getMessage());
        }
    }

    // ── LẤY DANH SÁCH TẤT CẢ PHIÊN ĐẤU GIÁ ──────────────────────
    public Message handleGetAuctions() {
        try {
            List<Map<String, Object>> rows = adminService.getAllAuctions();
            return new Message("ADMIN_AUCTIONS_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "data",    rows
            )));
        } catch (Exception e) {
            return error("ADMIN_AUCTIONS_RESPONSE", "Lỗi tải danh sách phiên đấu giá: " + e.getMessage());
        }
    }

    // ── XÓA BỎ MỘT SẢN PHẨM KHỎI HỆ THỐNG ──────────────────────────────────
    public Message handleDeleteProduct(String payload) {
        // Giải mã chuỗi JSON nhận được từ Client thành đối tượng DTO chứa mã sản phẩm
        AdminProductDto dto = gson.fromJson(payload, AdminProductDto.class);
        
        // SỬA ĐỔI: Ủy quyền lệnh xóa cứng/xóa mềm xuống dịch vụ AuctionProductService mới
        boolean ok = auctionProductService.deleteProduct(dto.productId());
        
        // Trả về trạng thái thực thi lệnh xóa cho Admin UI cập nhật lại bảng dữ liệu
        return new Message("ADMIN_DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xóa sản phẩm thành công khỏi sàn!" : "Xóa sản phẩm thất bại."
        )));
    }

    // ── XÓA TÀI KHOẢN NGƯỜI DÙNG VÀ CẤM TRUY CẬP ──────────────────────────────
    public Message handleDeleteUser(String payload, String currentUserId) {
        AdminUserDto dto = gson.fromJson(payload, AdminUserDto.class);

        // Quy tắc an toàn hệ thống (Business Rule): Admin không được phép tự xóa tài khoản của chính mình
        if (currentUserId.equals(dto.userId())) {
            return error("ADMIN_DELETE_USER_RESPONSE", "Không thể tự xóa tài khoản đang đăng nhập.");
        }

        // Thực thi lệnh xóa tài khoản thông qua AdminService chuyên trách bảng Users
        boolean ok = adminService.deleteUser(dto.userId());
        return new Message("ADMIN_DELETE_USER_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xóa tài khoản người dùng thành công!" : "Không tìm thấy mã tài khoản này."
        )));
    }

    // ── ĐÓNG PHIÊN ĐẤU GIÁ KHẨN CẤP (FORCE CLOSE) ─────────────────────
    public Message handleForceCloseAuction(String payload) {
        AdminAuctionDto dto = gson.fromJson(payload, AdminAuctionDto.class);

        long auctionId;
        try {
            // Chuyển đổi mã ID phiên đấu giá từ dạng chuỗi sang kiểu dữ liệu số nguyên Long
            auctionId = Long.parseLong(dto.auctionId());
        } catch (Exception e) {
            return error("ADMIN_CLOSE_AUCTION_RESPONSE", "Mã ID phiên đấu giá không đúng định dạng.");
        }

        // SỬA ĐỔI: Gọi dịch vụ AuctionProductService để kết thúc phiên cược lập tức, 
        // hàm này sẽ quét người trả giá cao nhất để tạo hóa đơn giao dịch (Transaction) trong DB.
        Map<String, Object> winner = auctionProductService.endAuction(auctionId);
        
        // Biên dịch câu thông báo kết quả trả về dựa trên việc phiên đó có người mua hay bị hủy do không ai đặt
        String resultMsg = winner != null
                ? "Đã cưỡng chế đóng phiên! Người thắng: " + winner.get("username")
                  + " — Mức giá chốt: " + String.format("%.0f VNĐ", winner.get("finalPrice"))
                : "Đã cưỡng chế đóng phiên (Phiên đấu giá bị hủy do không có ai đặt giá).";

        // TÍNH NĂNG ĐỒNG BỘ: Phát sóng tín hiệu "AUCTION_ENDED" đến toàn bộ các Client 
        // đang mở xem phòng đấu giá này để họ lập tức khóa nút bấm, hiển thị màn hình chúc mừng/kết thúc.
        server.broadcastToAuction(dto.auctionId(),
                new Message("AUCTION_ENDED", gson.toJson(Map.of(
                        "auctionId", auctionId,
                        "message",   resultMsg
                ))));

        // Phản hồi gói tin xác nhận về màn hình Console điều khiển của Admin
        return new Message("ADMIN_CLOSE_AUCTION_RESPONSE", gson.toJson(Map.of(
                "success", true,
                "message", resultMsg
        )));
    }

    // ── HÀM BỔ TRỢ ĐÓNG GÓI BẢN TIN LỖI NHANH ────────────────────────
    private Message error(String type, String message) {
        return new Message(type, gson.toJson(Map.of(
                "success", false, 
                "message", message
        )));
    }

    // ── CÁC CẤU TRÚC DỮ LIỆU NHẬN PAYLOAD (DTOs) ──────────────────────────
    private record AdminProductDto(String productId) {}
    private record AdminUserDto(String userId) {}
    private record AdminAuctionDto(String auctionId) {}
}