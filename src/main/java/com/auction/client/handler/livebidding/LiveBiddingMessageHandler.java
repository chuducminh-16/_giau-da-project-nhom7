package com.auction.client.handler.livebidding;

import com.auction.client.BidItem;
import com.auction.client.controller.LiveBiddingController;
import com.auction.client.network.Message;
import com.auction.client.session.AutoBidSession;
import com.auction.client.utils.ToastNotification;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 🛰️ BỘ XỬ LÝ GÓI TIN ĐẦU CUỐI (MESSAGE HANDLER ENGINE)
 * - Mục đích: Ôm trọn hàm `handleServerMessage` khổng lồ trước đây.
 * - Đọc payload từ Server, bóc tách JSON bằng GSON và điều phối cập nhật trạng thái UI tương ứng.
 */
public class LiveBiddingMessageHandler {

    private final LiveBiddingController controller; // Tham chiếu ngược về Controller gốc để can thiệp UI
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Lớp Record nội bộ dùng làm cấu trúc trung chuyển dữ liệu lịch sử gọn gàng
    private record HistoryEntry(String timeLabel, String username, String priceStr, double price) {}

    public LiveBiddingMessageHandler(LiveBiddingController controller) {
        this.controller = controller;
    }

    /**
     * Hàm phân phối trung tâm: Tiếp nhận tin nhắn và đẩy vào hàm xử lý nghiệp vụ chuyên biệt
     */
    public void processMessage(Message msg) {
        switch (msg.getType()) {
            case "PRODUCT_DETAIL_RESPONSE" -> handleProductDetail(msg);
            case "BID_HISTORY_RESPONSE"    -> handleBidHistory(msg);
            case "BID_UPDATE"              -> handleBidUpdate(msg);
            case "BID_RESULT"              -> handleBidResult(msg);
            case "TIME_EXTENDED", "AUCTION_EXTENDED" -> handleAuctionExtended(msg);
            case "AUCTION_ENDED"           -> handleAuctionEnded(msg);
        }
    }

    /**
     * Xử lý gói tin trả về thông tin chi tiết của sản phẩm đấu giá
     */
    private void handleProductDetail(Message msg) {
        try {
            JsonObject root = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                Platform.runLater(() -> controller.addLog("Lỗi: Không thể tải thông tin phòng!"));
                return;
            }
            
            // Ép kiểu payload thành Object Model Item tương ứng (Art, Electronics, Vehicle,...)
            Item item = controller.getGson().fromJson(root.get("item"), Item.class);
            if (item == null) return;

            Platform.runLater(() -> {
                controller.setCurrentItem(item);
                controller.setItemId(item.getId());
                if (controller.getLblProductName() != null) {
                    controller.getLblProductName().setText(item.getName());
                }

                // Nếu lịch sử chưa nạp kịp, lấy luôn giá khởi điểm làm mốc đầu tiên cho biểu đồ
                if (!controller.isHistoryLoaded()) {
                    controller.setLatestBid(item.getCurrentBid());
                    controller.updateCurrentBidLabel(item.getCurrentBid());
                    controller.getChartManager().addChartPoint(LocalTime.now().format(timeFmt), item.getCurrentBid());
                }

                controller.resetAndTargetNextBidPrompt();      // Làm mới ô nhập liệu gợi ý giá tối thiểu tiếp theo
                controller.getCountdownHandler().startCountdownFromItem(); // Kích hoạt đồng hồ đếm ngược
                controller.addLog("Vào phòng thành công: Đang theo dõi " + item.getName());

                // KIỂM TRA AUTO-BID: Nếu người dùng bật Auto-bid từ trước, kích hoạt chạy tiếp chiêu thức trả giá tự động
                if (controller.getAutoBidHandler().isAutoBidActive() && 
                    controller.getAutoBidHandler().getPendingAutoBidTriggerId() != null && 
                    controller.getSecondsRemaining() > 0) {
                    
                    controller.getAutoBidHandler().handleAutoBidIfNeeded(
                        controller.getAutoBidHandler().getPendingAutoBidTriggerId(), 
                        controller.getAutoBidHandler().getPendingAutoBidTriggerName()
                    );
                    // Thực hiện xong thì dọn dẹp biến chờ
                    controller.getAutoBidHandler().setPendingAutoBidTriggerId(null);
                    controller.getAutoBidHandler().setPendingAutoBidTriggerName(null);
                }
            });
        } catch (Exception e) {
            System.err.println("[LiveBiddingMessageHandler] Lỗi parse sản phẩm: " + e.getMessage());
        }
    }

    /**
     * Xử lý gói tin trả về danh sách lịch sử tất cả các lượt đặt giá trước đó
     */
    private void handleBidHistory(Message msg) {
        try {
            String payload = msg.getPayload();
            if (payload == null || payload.isBlank()) return;

            JsonArray arr = null;
            // Dự phòng cơ chế bọc cấu trúc mảng JSON tùy thuộc cấu hình Server trả về
            try {
                arr = controller.getGson().fromJson(payload, JsonArray.class);
            } catch (Exception e1) {
                try {
                    JsonObject root = controller.getGson().fromJson(payload, JsonObject.class);
                    if (root.has("history") && root.get("history").isJsonArray()) arr = root.getAsJsonArray("history");
                    else if (root.has("data") && root.get("data").isJsonArray()) arr = root.getAsJsonArray("data");
                } catch (Exception e2) {
                    return; // Nếu cấu trúc sai hoàn toàn thì thoát chặn crash
                }
            }

            if (arr == null || arr.size() == 0) return;

            List<HistoryEntry> entries = new ArrayList<>();
            double maxBid = 0;

            // Vòng lặp bóc từng bản ghi lịch sử đặt cược
            for (JsonElement el : arr) {
                JsonObject row = el.getAsJsonObject();
                String rawTime = safeStr(row, "createdAt", safeStr(row, "bidTime", ""));
                String username = safeStr(row, "username", "?");
                double price = safeDouble(row, "amount") > 0 ? safeDouble(row, "amount") : safeDouble(row, "bidPrice");
                
                String displayTime = convertUtcToVnTime(rawTime); // Đổi múi giờ UTC sang giờ VN (+7)
                entries.add(new HistoryEntry(displayTime, username, String.format("%,.0f VNĐ", price), price));
                
                if (price > maxBid) maxBid = price; // Tìm ra mức giá cao nhất trong lịch sử cũ
            }

            final double finalMaxBid = maxBid;
            final List<HistoryEntry> finalEntries = entries;
            final String topBidderUsername = finalEntries.isEmpty() ? "" : finalEntries.get(0).username();

            Platform.runLater(() -> {
                controller.setHistoryLoaded(true); // Đánh dấu đã nạp xong lịch sử phòng
                
                // Chuyển đổi dữ liệu nạp vào TableView hiển thị ở góc màn hình
                List<BidItem> rows = new ArrayList<>();
                for (HistoryEntry e : finalEntries) {
                    rows.add(new BidItem(e.timeLabel(), e.username(), e.priceStr()));
                }
                controller.getBidHistoryList().setAll(rows);

                // Cập nhật giá trần hiện tại của sản phẩm
                if (finalMaxBid > 0) {
                    controller.setLatestBid(finalMaxBid);
                    controller.updateCurrentBidLabel(finalMaxBid);
                    if (controller.getCurrentItem() != null) controller.getCurrentItem().setCurrentBid(finalMaxBid);
                    AutoBidSession.getInstance().updateLastKnownBid(finalMaxBid);
                    controller.resetAndTargetNextBidPrompt();
                }

                // Xóa đồ thị trắng rồi tiến hành vẽ các điểm lịch sử cũ theo thứ tự thời gian (Từ cũ nhất đến mới nhất)
                controller.getChartManager().clearChart();
                for (int i = finalEntries.size() - 1; i >= 0; i--) {
                    HistoryEntry e = finalEntries.get(i);
                    controller.getChartManager().addChartPoint(e.timeLabel(), e.price());
                }

                controller.addLog("Hệ thống đã tải lên " + finalEntries.size() + " lượt đặt giá trước đó.");

                // Đảm bảo chạy kịch bản Auto-bid nếu như người dùng bật tính năng tự động đặt giá từ trước
                if (controller.getAutoBidHandler().isAutoBidActive()) {
                    if (controller.getCurrentItem() != null && controller.getSecondsRemaining() != 0) {
                        controller.getAutoBidHandler().handleAutoBidIfNeeded("", topBidderUsername);
                    } else {
                        controller.getAutoBidHandler().setPendingAutoBidTriggerId("");
                        controller.getAutoBidHandler().setPendingAutoBidTriggerName(topBidderUsername);
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("[LiveBiddingMessageHandler] Lỗi xử lý lịch sử: " + e.getMessage());
        }
    }

    /**
     * Lắng nghe biến động thời gian thực (Real-time Broadcast) khi có một người khác trả giá cao hơn
     */
    private void handleBidUpdate(Message msg) {
        try {
            JsonObject dto = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            String productId = dto.get("productId").getAsString();
            
            // CHẶN TIN NHẮN RÁC: Nếu gói tin cập nhật của sản phẩm khác không thuộc phòng này, bỏ qua lập tức
            if (controller.getItemId() == null || !controller.getItemId().equals(productId)) return;

            double newBid = dto.get("newBid").getAsDouble();
            String bidderId = dto.has("bidderId") && !dto.get("bidderId").isJsonNull() ? dto.get("bidderId").getAsString() : "";
            String bidderName = dto.has("bidderName") ? dto.get("bidderName").getAsString() : "Đối thủ";

            Platform.runLater(() -> {
                // Đồng bộ mức giá trần mới
                controller.setLatestBid(newBid);
                if (controller.getCurrentItem() != null) controller.getCurrentItem().setCurrentBid(newBid);
                controller.updateCurrentBidLabel(newBid);
                AutoBidSession.getInstance().updateLastKnownBid(newBid);
                controller.resetAndTargetNextBidPrompt();

                // Thêm một dòng mới lên đầu bảng TableView lịch sử đặt cược
                String time = LocalTime.now().format(timeFmt);
                controller.getBidHistoryList().add(0, new BidItem(time, bidderName, String.format("%,.0f VNĐ", newBid)));

                // Vẽ thêm một chấm tọa độ mới lên đồ thị thời gian thực (Chấm này sẽ tự động nhuộm đỏ đặc ruột)
                controller.getChartManager().addChartPoint(time, newBid);
                controller.addLog(String.format("🔥 [%s] vừa trả giá cao hơn: %,.0f VNĐ", bidderName, newBid));

                // Hiển thị thông báo đẩy (Toast Notification) ở góc màn hình hệ điều hành để cảnh báo
                try {
                    Stage stage = (Stage) controller.getLblCountdown().getScene().getWindow();
                    String itemName = controller.getCurrentItem() != null ? controller.getCurrentItem().getName() : "Sản phẩm";
                    ToastNotification.bid(stage, bidderName, itemName, newBid);
                } catch (Exception ignored) {}

                // Đẩy thông tin đối thủ mới vào bộ lọc Auto-bid để xem có cần tự động tăng tiền đè lên không
                controller.getAutoBidHandler().handleAutoBidIfNeeded(bidderId, bidderName);
            });
        } catch (Exception e) {
            System.err.println("[LiveBiddingMessageHandler] Lỗi cập nhật giá mới: " + e.getMessage());
        }
    }

    /**
     * Nhận kết quả phản hồi của lệnh đặt giá do chính người dùng này bấm nút gửi lên
     */
    private void handleBidResult(Message msg) {
        try {
            JsonObject dto = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            boolean success = dto.get("success").getAsBoolean();
            String message = dto.has("message") ? dto.get("message").getAsString() : "";
            
            Platform.runLater(() -> {
                controller.addLog(success ? "✅ Lệnh đặt giá thủ công của bạn đã được Server chấp nhận!" : "❌ Đặt giá thất bại: " + message);
                controller.resetAndTargetNextBidPrompt();
            });
        } catch (Exception e) {
            System.err.println("[LiveBiddingMessageHandler] Lỗi nhận kết quả đặt cược: " + e.getMessage());
        }
    }

    /**
     * Kịch bản gia hạn thời gian (Sniper Protection): Nếu có ai đặt giá vào sát giờ chót, cuộc đấu gia hạn thêm thời gian
     */
    private void handleAuctionExtended(Message msg) {
        try {
            JsonObject dto = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            String newEndTimeStr = dto.has("newEndTime") ? dto.get("newEndTime").getAsString() : null;
            if (newEndTimeStr == null) return;

            LocalDateTime newEnd = LocalDateTime.parse(newEndTimeStr.replace(" ", "T"));
            long newSeconds = java.time.Duration.between(LocalDateTime.now(), newEnd).getSeconds();
            if (newSeconds <= 0) return;

            Platform.runLater(() -> {
                if (controller.getCurrentItem() != null) controller.getCurrentItem().setEndTime(newEnd);
                controller.setSecondsRemaining(newSeconds);
                controller.getCountdownHandler().startCountdownTimer(); // Tái khởi động luồng đếm ngược với mốc thời gian mới
                controller.addLog("⏰ Tin khẩn: Cuộc đua căng thẳng! Phiên đấu giá đã được tự động gia hạn thêm!");
            });
        } catch (Exception e) {
            System.err.println("[LiveBiddingMessageHandler] Lỗi gia hạn: " + e.getMessage());
        }
    }

    /**
     * Hồi còi chung cuộc: Nhận thông báo kết thúc phiên đấu giá từ máy chủ
     */
    private void handleAuctionEnded(Message msg) {
        Platform.runLater(() -> {
            controller.getCountdownHandler().stop(); // Tắt luồng chạy đếm ngược giây
            
            if (controller.getAutoBidHandler().isAutoBidActive()) {
                controller.getAutoBidHandler().deactivateAutoBid(); // Tắt chế độ Auto-bid tránh vòng lặp vô tận khi hết phiên
                controller.addLog("⚡ Hệ thống tự động tắt Auto-bid vì phòng đấu giá đã đóng cửa.");
            }
            controller.getCountdownHandler().onAuctionEnded(); // Chuyển giao diện nút sang trạng thái khóa "ĐÃ KẾT THÚC"

            try {
                JsonObject dto = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
                String winnerName = dto.has("winnerName") && !dto.get("winnerName").isJsonNull() ? dto.get("winnerName").getAsString() : null;
                double finalPrice = dto.has("finalPrice") ? dto.get("finalPrice").getAsDouble() : 0;
                String message = dto.has("message") && !dto.get("message").isJsonNull() ? dto.get("message").getAsString() : null;

                if (message != null) {
                    controller.addLog(message);
                } 

                // Luôn luôn hiện winner nếu có dữ liệu, bất kể message có hay không
                // Hien thi thong bao nguoi chien thang - day la phan quan trong bi bug
                if (winnerName != null && !winnerName.isBlank() && finalPrice > 0) {
                    controller.addLog(String.format("?? CHUC MUNG CHIEN THANG! Nguoi thang cuoc: %s | Gia chot: %,.0f VND", winnerName, finalPrice));
                } else {
                    controller.addLog("Phien ket thuc - Khong tim thay nguoi dat gia hop le.");
                }
                

                // Đẩy một hộp thoại chúc mừng lớn hiển thị trên màn hình nền Client
                try {
                    Stage stage = (Stage) controller.getLblCountdown().getScene().getWindow();
                    String itemName = controller.getCurrentItem() != null ? controller.getCurrentItem().getName() : "Sản phẩm";
                    if (winnerName != null && !winnerName.isEmpty()) {
                        ToastNotification.info(stage, "🏆 Kết quả chung cuộc", String.format("%s đã sở hữu thành công \"%s\"\nvới mức giá %,.0f VNĐ", winnerName, itemName, finalPrice));
                    }
                } catch (Exception ignored) {}
            } catch (Exception e) {
                controller.addLog("Phiên đấu giá kết thúc thành công!");
            }
        });
    }

    /**
     * Hàm tiện ích: Chuyển đổi định dạng giờ ISO UTC chuỗi String từ Server thành múi giờ Việt Nam (UTC+7) hiển thị UI
     */
    private String convertUtcToVnTime(String raw) {
        if (raw == null || raw.isBlank()) return "--:--:--";
        try {
            String normalized = raw.replace("T", " ").trim();
            String[] parts = normalized.split(" ");
            if (parts.length < 2) return raw;
            String[] timeParts = parts[1].split(":");
            int hour = Integer.parseInt(timeParts[0].trim());
            int minute = Integer.parseInt(timeParts[1].trim());
            int second = timeParts.length >= 3 ? Integer.parseInt(timeParts[2].trim().substring(0, Math.min(2, timeParts[2].trim().length()))) : 0;
            return String.format("%02d:%02d:%02d", (hour + 7) % 24, minute, second);
        } catch (Exception e) {
            return raw.length() >= 8 ? raw.substring(raw.length() - 8) : raw;
        }
    }

    /**
     * Hàm đọc an toàn giá trị String tránh lỗi NullPointerException từ đối tượng JSON JsonObject
     */
    private String safeStr(JsonObject obj, String key, String def) {
        try { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def; } catch (Exception e) { return def; }
    }

    /**
     * Hàm đọc an toàn giá trị Double số thực từ đối tượng JSON JsonObject
     */
    private double safeDouble(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsDouble() : 0; } catch (Exception e) { return 0; }
    }
}
