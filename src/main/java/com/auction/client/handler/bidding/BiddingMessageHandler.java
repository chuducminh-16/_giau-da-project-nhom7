package com.auction.client.handler.bidding;

import com.auction.client.model.BidItem;
import com.auction.client.network.Message;
import com.auction.client.utils.ToastNotification;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;
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
 * BiddingMessageHandler - Bo xu ly goi tin mang chung cho phong dau gia realtime.
 *
 * Gop lai tu LiveBiddingMessageHandler va SnipeGuardMessageHandler.
 * Su dung interface IBiddingController de hoat dong voi ca hai loai controller.
 */
public class BiddingMessageHandler {

    /**
     * Interface chung de handler giao tiep voi bat ky controller phong dau gia nao.
     */
    public interface IBiddingController {
        Item    getCurrentItem();
        void    setCurrentItem(Item item);
        void    setItemId(String id);
        String  getItemId();

        void    addLog(String message);
        void    updateCurrentBidLabel(double bid);
        void    populateProductInfo();

        boolean isHistoryLoaded();
        void    setHistoryLoaded(boolean state);
        void    setLatestBid(double bid);
        double  getLatestBid();
        void    setSecondsRemaining(long seconds);

        javafx.collections.ObservableList<BidItem> getBidHistory();

        void    addChartPoint(double price);

        void    startCountdownFromItem();
        void    stopCountdown();

        Gson    getGson();
        javafx.scene.control.Label getLblCountdown();

        com.auction.client.handler.livebidding.AutoBidHandler    getAutoBidHandler();
        com.auction.client.handler.livebidding.CountdownHandler  getCountdownHandler();

        // Hook mo rong cho SnipeGuard (default = khong lam gi, LiveBidding thuong override)
        default void onAuctionExtended(long newSeconds, int extendedBy, long wasSecondsLeft, String msg) {}
    }

    private final IBiddingController controller;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private record HistoryEntry(String timeLabel, String username, String priceStr, double price) {}

    public BiddingMessageHandler(IBiddingController controller) {
        this.controller = controller;
    }

    public void processMessage(Message msg) {
        switch (msg.getType()) {
            case "PRODUCT_DETAIL_RESPONSE"           -> handleProductDetail(msg);
            case "BID_HISTORY_RESPONSE"              -> handleBidHistory(msg);
            case "BID_UPDATE"                        -> handleBidUpdate(msg);
            case "BID_RESULT"                        -> handleBidResult(msg);
            case "TIME_EXTENDED", "AUCTION_EXTENDED" -> handleAuctionExtended(msg);
            case "AUCTION_ENDED"                     -> handleAuctionEnded(msg);
        }
    }

    private void handleProductDetail(Message msg) {
        try {
            JsonObject root = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                Platform.runLater(() -> controller.addLog("Loi: Khong the tai thong tin phong!"));
                return;
            }
            Item item = controller.getGson().fromJson(root.get("item"), Item.class);
            if (item == null) return;
            Platform.runLater(() -> {
                controller.setCurrentItem(item);
                controller.setItemId(item.getId());
                com.auction.client.network.NetworkClient.getInstance().send(
                    new Message("WATCH_AUCTION",
                        controller.getGson().toJson(java.util.Map.of("auctionId", item.getId())))
                );
                if (!controller.isHistoryLoaded()) {
                    controller.setLatestBid(item.getCurrentBid());
                    controller.updateCurrentBidLabel(item.getCurrentBid());
                    controller.addChartPoint(item.getCurrentBid());
                }
                controller.populateProductInfo();
                controller.startCountdownFromItem();
                controller.addLog("Chao mung vao phong: " + item.getName());
            });
        } catch (Exception e) {
            System.err.println("[BiddingMessageHandler] Loi parse PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
        }
    }

    private void handleBidHistory(Message msg) {
        try {
            String payload = msg.getPayload();
            if (payload == null || payload.isBlank()) return;

            JsonArray arr = null;

            // Thử parse trực tiếp thành mảng trước (Server trả về [{...}, {...}])
            try {
                arr = controller.getGson().fromJson(payload, JsonArray.class);
            } catch (Exception e1) {
                // Nếu không phải mảng thì thử parse thành object có wrapper
                try {
                    JsonObject root = controller.getGson().fromJson(payload, JsonObject.class);
                    if (root.has("history") && root.get("history").isJsonArray())
                        arr = root.getAsJsonArray("history");
                    else if (root.has("data") && root.get("data").isJsonArray())
                        arr = root.getAsJsonArray("data");
                } catch (Exception e2) {
                    return;
                }
            }

            if (arr == null || arr.size() == 0) return;


            List<HistoryEntry> entries = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                double price = safeDouble(o, "amount") > 0
                               ? safeDouble(o, "amount")
                               : safeDouble(o, "bidPrice"); // fallback tên field khác
                String timeLabel = convertUtcToVnTime(
                                   safeStr(o, "bidTime",
                                   safeStr(o, "createdAt", "")));
                String username  = safeStr(o, "username", "Unknown");
                entries.add(new HistoryEntry(timeLabel, username, String.format("%,.0f VND", price), price));
            }
            Platform.runLater(() -> {
                controller.setHistoryLoaded(true);
                controller.getBidHistory().clear();
                double latestPrice = 0;
                for (HistoryEntry e : entries) {
                    controller.getBidHistory().add(new BidItem(e.timeLabel(), e.username(), e.priceStr()));
                    if (e.price() > latestPrice) latestPrice = e.price();
                }
                if (latestPrice > 0) {
                    controller.setLatestBid(latestPrice);
                    controller.updateCurrentBidLabel(latestPrice);
                    controller.addChartPoint(latestPrice);
                }
            });
        } catch (Exception e) {
            System.err.println("[BiddingMessageHandler] Loi parse BID_HISTORY: " + e.getMessage());
        }
    }

    private void handleBidUpdate(Message msg) {
        try {
            JsonObject dto = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            String productId  = dto.get("productId").getAsString();
            Item currentItem  = controller.getCurrentItem();
            if (currentItem == null || !currentItem.getId().equals(productId)) return;
            double newBid     = dto.get("newBid").getAsDouble();
            String bidderId   = safeStr(dto, "bidderId", "");
            String bidderName = safeStr(dto, "bidderName", "Unknown");
            Platform.runLater(() -> {
                currentItem.setCurrentBid(newBid);
                controller.setLatestBid(newBid);
                controller.updateCurrentBidLabel(newBid);
                String time = LocalTime.now().format(timeFmt);
                controller.getBidHistory().add(0,
                    new BidItem(time, bidderName, String.format("%,.0f VND", newBid)));
                controller.addChartPoint(newBid);
                controller.addLog(String.format("%s vua dat %,.0f VND", bidderName, newBid));
                try {
                    Stage stage = (Stage) controller.getLblCountdown().getScene().getWindow();
                    String itemName = currentItem.getName() != null ? currentItem.getName() : "San pham";
                    ToastNotification.bid(stage, bidderName, itemName, newBid);
                } catch (Exception ignored) {}
                controller.getAutoBidHandler().handleAutoBidIfNeeded(bidderId, bidderName);
            });
        } catch (Exception e) {
            System.err.println("[BiddingMessageHandler] Loi cap nhat gia moi: " + e.getMessage());
        }
    }

    private void handleBidResult(Message msg) {
        try {
            JsonObject dto = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            boolean success = dto.get("success").getAsBoolean();
            String  message = dto.has("message") ? dto.get("message").getAsString() : "";
            Platform.runLater(() ->
                controller.addLog(success
                    ? "Dat gia thanh cong! Server da chap nhan."
                    : "Dat gia that bai: " + message));
        } catch (Exception e) {
            System.err.println("[BiddingMessageHandler] Loi nhan ket qua dat cuoc: " + e.getMessage());
        }
    }

    private void handleAuctionExtended(Message msg) {
        try {
            JsonObject dto      = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
            String newEndTimeStr= dto.has("newEndTime")    ? dto.get("newEndTime").getAsString()    : null;
            int    extendedBy   = dto.has("extendedBy")    ? dto.get("extendedBy").getAsInt()       : 60;
            long   wasSecondsLeft= dto.has("wasSecondsLeft")? dto.get("wasSecondsLeft").getAsLong() : 0;
            String message      = dto.has("message")       ? dto.get("message").getAsString()       : null;
            Item currentItem = controller.getCurrentItem();
            if (newEndTimeStr == null || currentItem == null) return;
            LocalDateTime newEnd   = LocalDateTime.parse(newEndTimeStr.replace(" ", "T"));
            long          newSeconds = java.time.Duration.between(LocalDateTime.now(), newEnd).getSeconds();
            if (newSeconds <= 0) return;
            Platform.runLater(() -> {
                currentItem.setEndTime(newEnd);
                controller.setSecondsRemaining(newSeconds);
                controller.stopCountdown();
                controller.startCountdownFromItem();
                controller.onAuctionExtended(newSeconds, extendedBy, wasSecondsLeft, message);
                String logMsg = message != null ? message
                        : String.format("SnipeGuard: phien gia han +%ds (bid luc con %ds)", extendedBy, wasSecondsLeft);
                controller.addLog(logMsg);
            });
        } catch (Exception e) {
            System.err.println("[BiddingMessageHandler] Loi gia han: " + e.getMessage());
        }
    }

    private void handleAuctionEnded(Message msg) {
        Platform.runLater(() -> {
            controller.getCountdownHandler().stop();
            if (controller.getAutoBidHandler().isAutoBidActive()) {
                controller.getAutoBidHandler().deactivateAutoBid();
                controller.addLog("He thong tu dong tat Auto-bid vi phong da dong cua.");
            }
            controller.getCountdownHandler().onAuctionEnded();
            try {
                JsonObject dto    = controller.getGson().fromJson(msg.getPayload(), JsonObject.class);
                String winnerName = dto.has("winnerName") && !dto.get("winnerName").isJsonNull()
                        ? dto.get("winnerName").getAsString() : null;
                double finalPrice = dto.has("finalPrice") ? dto.get("finalPrice").getAsDouble() : 0;
                String message    = dto.has("message")    && !dto.get("message").isJsonNull()
                        ? dto.get("message").getAsString() : null;
                if (message != null) controller.addLog(message);
                if (winnerName != null && !winnerName.isBlank() && finalPrice > 0) {
                    controller.addLog(String.format(
                        "CHUC MUNG CHIEN THANG! Nguoi thang cuoc: %s | Gia chot: %,.0f VND",
                        winnerName, finalPrice));
                } else if (winnerName == null || winnerName.isBlank()) {
                    controller.addLog("Phien ket thuc - Khong co nguoi dat gia hop le.");
                }
                try {
                    Stage stage = (Stage) controller.getLblCountdown().getScene().getWindow();
                    String itemName = controller.getCurrentItem() != null
                            ? controller.getCurrentItem().getName() : "San pham";
                    if (winnerName != null && !winnerName.isBlank()) {
                        ToastNotification.info(stage, "Ket qua chung cuoc",
                            String.format("%s so huu \"%s\" voi %,.0f VND", winnerName, itemName, finalPrice));
                    }
                } catch (Exception ignored) {}
            } catch (Exception e) {
                controller.addLog("Phien dau gia ket thuc!");
            }
        });
    }

    private String convertUtcToVnTime(String raw) {
        if (raw == null || raw.isBlank()) return "--:--:--";
        try {
            String[] parts = raw.replace("T", " ").trim().split(" ");
            if (parts.length < 2) return raw;
            String[] t = parts[1].split(":");
            int h = Integer.parseInt(t[0].trim());
            int m = Integer.parseInt(t[1].trim());
            int s = t.length >= 3 ? Integer.parseInt(t[2].trim().substring(0, Math.min(2, t[2].trim().length()))) : 0;
            return String.format("%02d:%02d:%02d", (h + 7) % 24, m, s);
        } catch (Exception e) { return raw.length() >= 8 ? raw.substring(raw.length() - 8) : raw; }
    }

    private String safeStr(JsonObject obj, String key, String def) {
        try { return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def; }
        catch (Exception e) { return def; }
    }

    private double safeDouble(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsDouble() : 0; }
        catch (Exception e) { return 0; }
    }
}