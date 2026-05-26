package com.auction.client.handler.snipeguard;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.auction.client.BidItem;
import com.auction.client.controller.SnipeGuardLiveBiddingController;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;

import javafx.application.Platform;

/**
 * 📡 BỘ XỬ LÝ GÓI TIN ĐẤU GIÁ REALTIME (SNIPE GUARD MESSAGE HANDLER)
 * - Vị trí: com.auction.client.handler.snipeguard
 * - Nhiệm vụ: Tách biệt toàn bộ tầng xử lý JSON payload và lắng nghe sự kiện mạng ra khỏi UI Controller.
 */
public class SnipeGuardMessageHandler {

    private final SnipeGuardLiveBiddingController controller;
    private final Gson gson;
    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    public SnipeGuardMessageHandler(SnipeGuardLiveBiddingController controller) {
        this.controller = controller;
        this.gson = buildGson();
    }

    /** Cấu hình Gson tùy biến để nhận diện đa hình các lớp con của Item (Art, Electronics, Vehicle) */
    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Item.class, (JsonDeserializer<Item>) (json, typeOfT, ctx) -> {
                    JsonObject obj = json.getAsJsonObject();
                    String type = obj.has("type") && !obj.get("type").isJsonNull()
                            ? obj.get("type").getAsString().toUpperCase() : "ART";
                    return switch (type) {
                        case "ELECTRONICS" -> ctx.deserialize(obj, Electronics.class);
                        case "VEHICLE"     -> ctx.deserialize(obj, Vehicle.class);
                        default            -> ctx.deserialize(obj, Art.class);
                    };
                })
                .create();
    }

    public Gson getGson() {
        return this.gson;
    }

    /**
     * Điểm thu nhận và phân luồng xử lý gói tin mạng từ Server truyền về phòng đấu giá
     */
    public void handleServerMessage(Message msg) {
        switch (msg.getType()) {
            
            case "PRODUCT_DETAIL_RESPONSE" -> {
                try {
                    JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                    if (!root.has("success") || !root.get("success").getAsBoolean()) {
                        Platform.runLater(() -> controller.addLog("❌ Không tìm thấy sản phẩm!"));
                        return;
                    }
                    Item item = gson.fromJson(root.get("item"), Item.class);
                    if (item == null) return;

                    Platform.runLater(() -> {
                        controller.setCurrentItem(item);
                        controller.populateProductInfo();
                        controller.startCountdownFromItem();
                        
                        // Đăng ký nhận luồng sự kiện Realtime của riêng phòng đấu giá này
                        client.send(new Message("WATCH_AUCTION",
                                gson.toJson(Map.of("auctionId", item.getId()))));
                        
                        controller.addChartPoint(item.getCurrentBid());
                        controller.addLog("✅ Chào mừng vào phòng: " + item.getName());
                    });
                } catch (Exception e) {
                    System.err.println("[SnipeGuardLive] Lỗi parse PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
                }
            }

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();
                    
                    Item currentItem = controller.getCurrentItem();
                    if (currentItem == null || !currentItem.getId().equals(productId)) return;

                    double newBid = dto.get("newBid").getAsDouble();
                    String bidderName = dto.has("bidderName") ? dto.get("bidderName").getAsString() : "Unknown";

                    Platform.runLater(() -> {
                        currentItem.setCurrentBid(newBid);
                        controller.updateCurrentBidLabel(newBid);
                        
                        String time = LocalTime.now().format(timeFmt);
                        controller.getBidHistory().add(0, new BidItem(time, bidderName, String.format("%,.0f VNĐ", newBid)));
                        
                        controller.addChartPoint(newBid);
                        controller.addLog(String.format("💰 %s vừa đặt %,.0f VNĐ", bidderName, newBid));
                    });
                } catch (Exception e) {
                    System.err.println("[SnipeGuardLive] Lỗi parse BID_UPDATE: " + e.getMessage());
                }
            }

            case "BID_RESULT" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    boolean success = dto.get("success").getAsBoolean();
                    String message = dto.has("message") ? dto.get("message").getAsString() : "";
                    Platform.runLater(() -> 
                            controller.addLog(success ? "✅ Đặt giá thành công!" : "❌ Thất bại: " + message));
                } catch (Exception e) {
                    System.err.println("[SnipeGuardLive] Lỗi parse BID_RESULT: " + e.getMessage());
                }
            }

            case "AUCTION_EXTENDED" -> handleAuctionExtended(msg.getPayload());

            case "AUCTION_ENDED" -> {
                Platform.runLater(() -> {
                    controller.stopCountdown();
                    controller.markAuctionEnded();

                    try {
                        JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                        String winnerName = dto.has("winnerName") && !dto.get("winnerName").isJsonNull()
                                ? dto.get("winnerName").getAsString() : null;
                        double finalPrice = dto.has("finalPrice") ? dto.get("finalPrice").getAsDouble() : 0;
                        String message = dto.has("message") && !dto.get("message").isJsonNull()
                                ? dto.get("message").getAsString() : null;

                        if (message != null) {
                            controller.addLog(message);
                        } else if (winnerName != null && finalPrice > 0) {
                            controller.addLog(String.format("🏆 Người thắng: %s — Giá: %,.0f VNĐ", winnerName, finalPrice));
                        } else {
                            controller.addLog("Phiên kết thúc — không có người đặt giá.");
                        }
                    } catch (Exception e) {
                        controller.addLog("🏁 Phiên đấu giá đã kết thúc!");
                    }
                });
            }
        }
    }

    /** ⚡ TÍNH NĂNG SNIPEGUARD: Kéo dài thời gian khi phát hiện hành vi bắn tỉa giá giây cuối */
    private void handleAuctionExtended(String payload) {
        try {
            JsonObject dto = gson.fromJson(payload, JsonObject.class);

            String newEndTimeStr = dto.has("newEndTime") ? dto.get("newEndTime").getAsString() : null;
            int extendedBy = dto.has("extendedBy") ? dto.get("extendedBy").getAsInt() : 60;
            long wasSecondsLeft = dto.has("wasSecondsLeft") ? dto.get("wasSecondsLeft").getAsLong() : 0;
            String message = dto.has("message") ? dto.get("message").getAsString() : null;

            Item currentItem = controller.getCurrentItem();
            if (newEndTimeStr == null || currentItem == null) return;

            LocalDateTime newEnd = LocalDateTime.parse(newEndTimeStr.replace(" ", "T"));
            long newSeconds = java.time.Duration.between(LocalDateTime.now(), newEnd).getSeconds();
            if (newSeconds <= 0) return;

            Platform.runLater(() -> {
                currentItem.setEndTime(newEnd);
                controller.setSecondsRemaining(newSeconds);

                // Đồng bộ và làm mới lại luồng Timeline đếm ngược của UI
                controller.stopCountdown();
                controller.startCountdownTimer();

                // Tạo hiệu ứng chớp nháy đổi màu cam cảnh báo cơ chế SnipeGuard đã kích hoạt
                controller.flashExtendedAnimation(extendedBy);

                String logMsg = message != null ? message
                        : String.format("⏰ SnipeGuard: phiên gia hạn +%ds (bid lúc còn %ds)", extendedBy, wasSecondsLeft);
                controller.addLog(logMsg);
            });
        } catch (Exception e) {
            System.err.println("[SnipeGuardLive] Lỗi parse AUCTION_EXTENDED: " + e.getMessage());
        }
    }
}