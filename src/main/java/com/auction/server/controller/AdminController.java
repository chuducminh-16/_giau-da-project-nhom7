package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AdminService;
import com.auction.server.service.AuctionService;
import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

/**
 * Xử lý tất cả request từ ADMIN.
 * Nhận payload, gọi service, trả về Message.
 */
public class AdminController {

    private final AdminService   adminService   = new AdminService();
    private final AuctionService auctionService = new AuctionService();
    private final NetworkServer  server;
    private final Gson           gson = new Gson();

    public AdminController(NetworkServer server) {
        this.server = server;
    }

    // ── Lấy tất cả sản phẩm ───────────────────────────
    public Message handleGetProducts() {
        try {
            List<Map<String, Object>> rows = adminService.getAllProducts();
            return new Message("ADMIN_PRODUCTS_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "data",    rows
            )));
        } catch (Exception e) {
            return error("ADMIN_PRODUCTS_RESPONSE",
                    "Lỗi tải sản phẩm: " + e.getMessage());
        }
    }

    // ── Lấy tất cả users ──────────────────────────────
    public Message handleGetUsers() {
        try {
            List<Map<String, Object>> rows = adminService.getAllUsers();
            return new Message("ADMIN_USERS_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "data",    rows
            )));
        } catch (Exception e) {
            return error("ADMIN_USERS_RESPONSE",
                    "Lỗi tải users: " + e.getMessage());
        }
    }

    // ── Lấy tất cả phiên đấu giá ──────────────────────
    public Message handleGetAuctions() {
        try {
            List<Map<String, Object>> rows = adminService.getAllAuctions();
            return new Message("ADMIN_AUCTIONS_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "data",    rows
            )));
        } catch (Exception e) {
            return error("ADMIN_AUCTIONS_RESPONSE",
                    "Lỗi tải phiên: " + e.getMessage());
        }
    }

    // ── Xóa sản phẩm ──────────────────────────────────
    public Message handleDeleteProduct(String payload) {
        AdminProductDto dto = gson.fromJson(payload, AdminProductDto.class);
        boolean ok = auctionService.deleteProduct(dto.productId());
        return new Message("ADMIN_DELETE_PRODUCT_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xóa sản phẩm!" : "Xóa thất bại."
        )));
    }

    // ── Xóa user ──────────────────────────────────────
    // currentUserId truyền vào để kiểm tra không tự xóa chính mình
    public Message handleDeleteUser(String payload, String currentUserId) {
        AdminUserDto dto = gson.fromJson(payload, AdminUserDto.class);

        if (currentUserId.equals(dto.userId())) {
            return error("ADMIN_DELETE_USER_RESPONSE",
                    "Không thể xóa tài khoản đang đăng nhập.");
        }

        boolean ok = adminService.deleteUser(dto.userId());
        return new Message("ADMIN_DELETE_USER_RESPONSE", gson.toJson(Map.of(
                "success", ok,
                "message", ok ? "Đã xóa tài khoản!" : "Không tìm thấy tài khoản."
        )));
    }

    // ── Force close phiên đấu giá ─────────────────────
    public Message handleForceCloseAuction(String payload) {
        AdminAuctionDto dto = gson.fromJson(payload, AdminAuctionDto.class);

        long auctionId;
        try {
            auctionId = Long.parseLong(dto.auctionId());
        } catch (Exception e) {
            return error("ADMIN_CLOSE_AUCTION_RESPONSE", "ID phiên không hợp lệ.");
        }

        Map<String, Object> winner = auctionService.endAuction(auctionId);
        String resultMsg = winner != null
                ? "Đã đóng phiên! Winner: " + winner.get("username")
                  + " — " + String.format("%.0f VNĐ", winner.get("finalPrice"))
                : "Đã đóng phiên (không có bid nào, đã hủy).";

        // Broadcast cho client đang xem phiên
        server.broadcastToAuction(dto.auctionId(),
                new Message("AUCTION_ENDED", gson.toJson(Map.of(
                        "auctionId", auctionId,
                        "message",   resultMsg
                ))));

        return new Message("ADMIN_CLOSE_AUCTION_RESPONSE", gson.toJson(Map.of(
                "success", true,
                "message", resultMsg
        )));
    }

    // ── Helper ────────────────────────────────────────
    private Message error(String type, String message) {
        return new Message(type, gson.toJson(Map.of(
                "success", false, "message", message
        )));
    }

    // ── DTOs ──────────────────────────────────────────
    private record AdminProductDto(String productId) {}
    private record AdminUserDto(String userId) {}
    private record AdminAuctionDto(String auctionId) {}
}