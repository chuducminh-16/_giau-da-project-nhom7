package com.auction.client.handler.admin;

import com.auction.client.controller.AdminController;
import com.auction.client.network.Message;
import com.google.gson.Gson;
import javafx.application.Platform;
import java.util.List;
import java.util.Map;

/**
 * 📡 BỘ ĐIỀU PHỐI TIN NHẮN ADMIN (ADMIN MESSAGE HANDLER)
 * - Nhiệm vụ: Giải phóng Controller khỏi gánh nặng xử lý JSON/Gson và tính toán số liệu thống kê.
 * - Mọi dữ liệu sau khi xử lý xong sẽ được đồng bộ an toàn về luồng hiển thị (JavaFX Application Thread).
 */
public class AdminMessageHandler {

    private final AdminController controller;
    private final Gson gson;

    public AdminMessageHandler(AdminController controller) {
        this.controller = controller;
        this.gson = new Gson();
    }

    /**
     * Hàm trung tâm tiếp nhận gói tin từ NetworkClient và phân phối xử lý bất đồng bộ
     */
    public void handleServerMessage(Message msg) {
        // Luôn luôn bọc trong Platform.runLater khi muốn cập nhật dữ liệu lên TableView/Label của JavaFX
        Platform.runLater(() -> {
            switch (msg.getType()) {
                // ── 1. Đón nhận danh sách dữ liệu thô từ Server ─────────────────
                case "ADMIN_PRODUCTS_RESPONSE" -> handleProductsResponse(msg);
                case "ADMIN_USERS_RESPONSE"    -> handleUsersResponse(msg);
                case "ADMIN_AUCTIONS_RESPONSE" -> handleAuctionsResponse(msg);

                // ── 2. Đón nhận phản hồi từ các lệnh điều hướng/xóa ──────────────
                case "ADMIN_DELETE_PRODUCT_RESPONSE" -> handleDeleteProductResponse(msg);
                case "ADMIN_DELETE_USER_RESPONSE"    -> handleDeleteUserResponse(msg);
                case "ADMIN_CLOSE_AUCTION_RESPONSE"  -> handleCloseAuctionResponse(msg);
            }
        });
    }

    private void handleProductsResponse(Message msg) {
        AdminListResponse resp = gson.fromJson(msg.getPayload(), AdminListResponse.class);
        if (resp.success && resp.data != null) {
            // Đổ thẳng dữ liệu sạch vào danh sách quan sát của Controller
            controller.getAllProducts().setAll(resp.data);
            updateProductStats(resp.data);
            controller.setStatus("Tải " + resp.data.size() + " sản phẩm thành công.");
        } else {
            controller.setStatus("Lỗi tải sản phẩm: " + resp.message);
        }
    }

    private void handleUsersResponse(Message msg) {
        AdminListResponse resp = gson.fromJson(msg.getPayload(), AdminListResponse.class);
        if (resp.success && resp.data != null) {
            controller.getAllUsers().setAll(resp.data);
            updateUserStats(resp.data);
            controller.setStatus("Tải " + resp.data.size() + " tài khoản thành công.");
        } else {
            controller.setStatus("Lỗi tải người dùng: " + resp.message);
        }
    }

    private void handleAuctionsResponse(Message msg) {
        AdminListResponse resp = gson.fromJson(msg.getPayload(), AdminListResponse.class);
        if (resp.success && resp.data != null) {
            controller.getAllAuctions().setAll(resp.data);
            // Mặc định ban đầu hiển thị toàn bộ phiên đấu giá chưa qua bộ lọc
            controller.getFilteredAuctions().setAll(resp.data);
            updateAuctionStats(resp.data);
            controller.setStatus("Tải " + resp.data.size() + " phiên đấu giá thành công.");
        } else {
            controller.setStatus("Lỗi tải phiên: " + resp.message);
        }
    }

    private void handleDeleteProductResponse(Message msg) {
        SimpleResponse resp = gson.fromJson(msg.getPayload(), SimpleResponse.class);
        controller.showProductStatus(resp.success ? "✓ " + resp.message : "⚠ " + resp.message, !resp.success);
        // Nếu xóa thành công, ra lệnh cho Controller phát tín hiệu mạng tải lại danh sách mới
        if (resp.success) controller.loadProducts();
    }

    private void handleDeleteUserResponse(Message msg) {
        SimpleResponse resp = gson.fromJson(msg.getPayload(), SimpleResponse.class);
        controller.showUserStatus(resp.success ? "✓ " + resp.message : "⚠ " + resp.message, !resp.success);
        if (resp.success) controller.loadUsers();
    }

    private void handleCloseAuctionResponse(Message msg) {
        SimpleResponse resp = gson.fromJson(msg.getPayload(), SimpleResponse.class);
        controller.showAuctionStatus(resp.success ? "✓ " + resp.message : "⚠ " + resp.message, !resp.success);
        if (resp.success) controller.loadAuctions();
    }

    // ── 📊 LOGIC TÍNH TOÁN SỐ LIỆU THỐNG KÊ (STATS) ──────────────────────────

    private void updateProductStats(List<Map<String, Object>> products) {
        long total = products.size();
        long active = products.stream().filter(p -> "RUNNING".equals(p.get("status")) || "OPEN".equals(p.get("status"))).count();
        long pending = products.stream().filter(p -> "PENDING".equals(p.get("status"))).count();

        // Đẩy chuỗi text hiển thị ra các Label tương ứng trên UI
        controller.setLabelText(controller.getLblTotalProducts(), String.valueOf(total));
        controller.setLabelText(controller.getLblActiveProducts(), String.valueOf(active));
        controller.setLabelText(controller.getLblPendingProducts(), String.valueOf(pending));
        controller.setLabelText(controller.getLblStatProducts(), "📦 Sản phẩm: " + total);
    }

    private void updateUserStats(List<Map<String, Object>> users) {
        long total = users.size();
        long bidders = users.stream().filter(u -> "BIDDER".equals(u.get("role"))).count();
        long sellers = users.stream().filter(u -> "SELLER".equals(u.get("role"))).count();

        controller.setLabelText(controller.getLblTotalUsers(), String.valueOf(total));
        controller.setLabelText(controller.getLblTotalBidders(), String.valueOf(bidders));
        controller.setLabelText(controller.getLblTotalSellers(), String.valueOf(sellers));
        controller.setLabelText(controller.getLblStatUsers(), "👥 Người dùng: " + total);
    }

    private void updateAuctionStats(List<Map<String, Object>> auctions) {
        long running = auctions.stream().filter(a -> "RUNNING".equals(a.get("status"))).count();
        long open = auctions.stream().filter(a -> "OPEN".equals(a.get("status"))).count();
        long finished = auctions.stream().filter(a -> "FINISHED".equals(a.get("status")) || "PAID".equals(a.get("status"))).count();
        long canceled = auctions.stream().filter(a -> "CANCELED".equals(a.get("status"))).count();

        controller.setLabelText(controller.getLblRunningAuctions(), String.valueOf(running));
        controller.setLabelText(controller.getLblOpenAuctions(), String.valueOf(open));
        controller.setLabelText(controller.getLblFinishedAuctions(), String.valueOf(finished));
        controller.setLabelText(controller.getLblCanceledAuctions(), String.valueOf(canceled));
        controller.setLabelText(controller.getLblStatAuctions(), "🔨 Phiên: " + auctions.size());
    }

    public Gson getGson() { return gson; }

    // ── 📦 ĐÓNG GÓI CẤU TRÚC RECORD PHỤ TRỢ DỮ LIỆU (DTO) ─────────────────────
    private record AdminListResponse(boolean success, String message, List<Map<String, Object>> data) {}
    private record SimpleResponse(boolean success, String message) {}
}