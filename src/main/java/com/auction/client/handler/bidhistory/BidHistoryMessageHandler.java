package com.auction.client.handler.bidhistory;

import com.auction.client.controller.BidHistoryController;
import com.auction.client.controller.BidHistoryController.BidRecord;
import com.auction.client.network.Message;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 📡 BỘ XỬ LÝ GÓI TIN LỊCH SỬ (BID HISTORY MESSAGE HANDLER)
 * - Nhiệm vụ: Xử lý mạng, chuyển đổi Gson/JSON, quản lý logic trạng thái thời gian thực.
 */
public class BidHistoryMessageHandler {

    private final BidHistoryController controller;
    private final Gson gson;

    public BidHistoryMessageHandler(BidHistoryController controller) {
        this.controller = controller;
        this.gson = new Gson();
    }

    /** Đón nhận gói tin lịch sử trả về từ Server */
    public void handleServerMessage(Message msg) {
        if (!"USER_BID_HISTORY_RESPONSE".equals(msg.getType())) return;

        Platform.runLater(() -> {
            try {
                String payload = msg.getPayload();
                if (payload == null || payload.isBlank()) {
                    controller.getAllRecords().clear();
                    controller.getDisplayData().clear();
                    updateStats();
                    controller.setStatus("Không có lịch sử đấu giá.");
                    return;
                }

                JsonArray arr = gson.fromJson(payload, JsonArray.class);
                List<BidRecord> records = new ArrayList<>();
                
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    records.add(new BidRecord(
                            safeStr(obj, "itemId", ""),
                            safeStr(obj, "itemName", "Không rõ"),
                            safeDouble(obj, "myBid"),
                            safeDouble(obj, "finalPrice"),
                            safeStr(obj, "winnerId", ""),
                            safeStr(obj, "status", ""),
                            safeStr(obj, "endTime", ""),
                            safeStr(obj, "sellerName", "")
                    ));
                }

                controller.getAllRecords().setAll(records);
                controller.getDisplayData().setAll(records);
                updateStats();
                controller.setStatus("Tải " + records.size() + " phiên thành công.");

            } catch (Exception e) {
                controller.showError("⚠ Lỗi parse dữ liệu: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /** Tính toán tổng hợp số liệu hiển thị lên các thẻ thống kê UI */
    public void updateStats() {
        String myId = UserSession.getInstance().getUserId();
        List<BidRecord> allRecords = controller.getAllRecords();
        
        long total = allRecords.size();

        // Đang diễn ra = Status OPEN/RUNNING và đồng thời thời gian chưa hết hạn
        long active = allRecords.stream()
                .filter(r -> ("RUNNING".equals(r.status()) || "OPEN".equals(r.status())) && !isExpired(r.endTime()))
                .count();

        // Đã thắng = Mình là winner và (Phiên đổi status đóng hoặc thời gian đã chạy qua)
        long won = allRecords.stream()
                .filter(r -> myId.equals(r.winnerId()) && (isFinishedStatus(r.status()) || isExpired(r.endTime())))
                .count();

        // Đã thua = Phiên đã dừng nhưng mình không phải winner
        long lost = allRecords.stream()
                .filter(r -> (isFinishedStatus(r.status()) || isExpired(r.endTime())) && !myId.equals(r.winnerId()))
                .count();

        double totalBid = allRecords.stream().mapToDouble(BidRecord::myBid).sum();

        // Đẩy ngược chuỗi text sạch ra màn hình hiển thị thông qua Controller
        controller.setLabelText(controller.getCardTotal(), String.valueOf(total));
        controller.setLabelText(controller.getCardWon(), String.valueOf(won));
        controller.setLabelText(controller.getCardLost(), String.valueOf(lost));
        controller.setLabelText(controller.getCardTotalBid(), String.format("%,.0f VNĐ", totalBid));

        controller.setLabelText(controller.getLblStatTotal(), "📋 Tổng: " + total);
        controller.setLabelText(controller.getLblStatWon(), "🏆 Đã thắng: " + won);
        controller.setLabelText(controller.getLblStatLost(), "❌ Đã thua: " + lost);
        controller.setLabelText(controller.getLblStatActive(), "🔴 Đang diễn ra: " + active);
    }

    /** Kiểm tra xem mình có phải người chiến thắng bản ghi này không */
    public boolean isWinner(BidRecord rec) {
        return UserSession.getInstance().getUserId().equals(rec.winnerId());
    }

    /** Kiểm tra trạng thái đóng cứng từ Database */
    public boolean isFinishedStatus(String status) {
        return "FINISHED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status);
    }

    /** Kiểm tra đối chiếu thời gian máy khách xem phiên thực tế đã lố giờ kết thúc chưa */
    public boolean isExpired(String endTimeStr) {
        if (endTimeStr == null || endTimeStr.isBlank()) return false;
        try {
            String normalized = endTimeStr.replace(" ", "T");
            if (normalized.length() > 19) normalized = normalized.substring(0, 19);
            return LocalDateTime.now().isAfter(LocalDateTime.parse(normalized));
        } catch (Exception e) {
            return false;
        }
    }

    /** Quyết định chuỗi văn bản trạng thái hiển thị dựa trên so sánh thời gian thực tế */
    public String resolveResult(BidRecord rec) {
        String status = rec.status();
        boolean expired = isExpired(rec.endTime());

        if (("RUNNING".equals(status) || "OPEN".equals(status)) && expired) {
            return isWinner(rec) ? "🏆 Thắng" : "❌ Thua";
        }
        if (("RUNNING".equals(status) || "OPEN".equals(status)) && !expired) {
            return "🔴 Đang diễn ra";
        }
        return isWinner(rec) ? "🏆 Thắng" : "❌ Thua";
    }

    // ── 🛠️ GSON PARSE HELPERS ───────────────────────────────────────────────
    private String safeStr(JsonObject obj, String key, String def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
        return obj.get(key).getAsString();
    }

    private double safeDouble(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return 0;
        try { return obj.get(key).getAsDouble(); } catch (Exception e) { return 0; }
    }
}