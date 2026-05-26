package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.service.AutoBidService;
import com.google.gson.Gson;
import java.util.Map;

/**
 * 🤖 AUTOBID CONTROLLER (BỘ ĐIỀU PHỐI BOT TỰ ĐỘNG ĐẶT GIÁ)
 * - Vị trí thư mục: com.auction.server.controller
 * - Trách nhiệm: Tiếp nhận các yêu cầu thiết lập cấu hình hoặc hủy bỏ trạng thái
 * hoạt động của hệ thống Bot tự động đấu giá (Auto-Bid) cho từng tài khoản Bidder.
 */
public class AutoBidController {

    private final AutoBidService autoBidService = new AutoBidService();
    private final Gson gson = new Gson();

    /**
     * ⚙️ ĐĂNG KÝ/KÍCH HOẠT CẤU HÌNH BOT ĐẶT GIÁ TỰ ĐỘNG
     * Ngân sách tối đa (maxBid) và bước nhảy (increment) phải được xác thực chặt chẽ.
     */
    public Message handleRegisterAutoBid(String payload, String bidderId) {
        try {
            AutoBidDto dto = gson.fromJson(payload, AutoBidDto.class);
            
            // Xác thực nghiệp vụ (Business Validation)
            if (dto.maxBid() <= 0) return autoBidResult(false, "Ngân sách tối đa (maxBid) phải lớn hơn 0.");
            if (dto.increment() <= 0) return autoBidResult(false, "Bước nhảy giá tiền phải lớn hơn 0.");
            
            // Đăng ký thông số cấu hình Bot vào hệ thống lưu trữ/bộ nhớ đệm Cache
            boolean ok = autoBidService.registerAutoBid(dto.itemId(), bidderId, dto.maxBid(), dto.increment());
            return autoBidResult(ok, ok ? "Đã thiết lập cấu hình Bot tự động đặt giá thành công!" : "Cấu hình Bot thất bại.");
        } catch (Exception e) {
            return autoBidResult(false, "Gặp lỗi hệ thống: " + e.getMessage());
        }
    }

    /**
     * 🛑 HỦY BỎ TRẠNG THÁI HOẠT ĐỘNG CỦA BOT TỰ ĐỘNG
     */
    public Message handleCancelAutoBid(String payload, String bidderId) {
        try {
            CancelAutoBidDto dto = gson.fromJson(payload, CancelAutoBidDto.class);
            
            // Xóa cấu hình Bot ra khỏi danh sách giám sát phòng đấu giá
            boolean ok = autoBidService.cancelAutoBid(dto.itemId(), bidderId);
            return autoBidResult(ok, ok ? "Đã hủy hoạt động của Bot thành công." : "Không tìm thấy cấu hình Bot nào để hủy.");
        } catch (Exception e) {
            return autoBidResult(false, "Gặp lỗi hệ thống khi hủy Bot: " + e.getMessage());
        }
    }

    /** Hàm tiện ích đóng gói nhanh thông báo trạng thái của Bot tự động */
    private Message autoBidResult(boolean success, String message) {
        return new Message("AUTO_BID_RESULT", gson.toJson(Map.of("success", success, "message", message)));
    }

    // ── CÁC CẤU TRÚC DTO DÀNH RIÊNG CHO LUỒNG BOT ──────────────────────────
    private record AutoBidDto(String itemId, double maxBid, double increment) {}
    private record CancelAutoBidDto(String itemId) {}
}