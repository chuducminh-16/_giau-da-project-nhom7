package com.auction.client.handler;

import com.auction.client.session.UserSession;
import com.auction.client.utils.ToastNotification;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.stage.Stage;

import java.util.function.Consumer;

/**
 * AuctionEndedHandler — xử lý sự kiện AUCTION_ENDED từ server.
 * Dùng ToastNotification (góc phải dưới) thay vì WinnerToastNotification.
 */
public class AuctionEndedHandler {

    private final Gson             gson;
    private final StageSupplier    stageSupplier;
    private final ItemSupplier     itemSupplier;
    private final Runnable         onEnded;
    private final Consumer<String> onLog;

    @FunctionalInterface
    public interface StageSupplier { Stage get(); }

    @FunctionalInterface
    public interface ItemSupplier { Item get(); }

    public AuctionEndedHandler(Gson gson,
                                StageSupplier stageSupplier,
                                ItemSupplier  itemSupplier,
                                Runnable      onEnded,
                                Consumer<String> onLog) {
        this.gson          = gson;
        this.stageSupplier = stageSupplier;
        this.itemSupplier  = itemSupplier;
        this.onEnded       = onEnded;
        this.onLog         = onLog;
    }

    public void handle(String payload) {
        System.out.println("[AuctionEndedHandler] handle() được gọi. Payload: " + payload);

        if (onEnded != null) onEnded.run();

        AuctionEndedData data = parsePayload(payload);
        logResult(data);
        showToast(data);
    }

    private AuctionEndedData parsePayload(String payload) {
        try {
            JsonObject dto = gson.fromJson(payload, JsonObject.class);

            String winnerName = safeStr(dto, "winnerName");
            String winnerId   = safeStr(dto, "winnerId");
            String itemId     = safeStr(dto, "itemId");
            String message    = safeStr(dto, "message");
            double finalPrice = safeDouble(dto, "finalPrice");

            String itemName = "";
            Item current = itemSupplier != null ? itemSupplier.get() : null;
            if (current != null && current.getName() != null) {
                itemName = current.getName();
            }
            if (itemName.isBlank()) itemName = safeStr(dto, "itemName");
            if (itemName.isBlank()) itemName = "sản phẩm";

            return new AuctionEndedData(winnerName, winnerId, itemId, itemName, finalPrice, message);

        } catch (Exception e) {
            System.err.println("[AuctionEndedHandler] Lỗi parse payload: " + e.getMessage());
            String fallbackName = "";
            Item current = itemSupplier != null ? itemSupplier.get() : null;
            if (current != null && current.getName() != null) fallbackName = current.getName();
            return new AuctionEndedData("", "", "", fallbackName, 0, payload);
        }
    }

    private void logResult(AuctionEndedData data) {
        if (onLog == null) return;

        if (data.message() != null && !data.message().isBlank()) {
            onLog.accept(data.message());
        } else if (data.winnerName() != null && !data.winnerName().isBlank()) {
            onLog.accept(String.format("🏆 Phiên kết thúc! %s thắng với %,.0f VNĐ",
                    data.winnerName(), data.finalPrice()));
        } else {
            onLog.accept("📭 Phiên đấu giá kết thúc — không có người tham gia.");
        }
    }

    private void showToast(AuctionEndedData data) {
        Stage stage = stageSupplier != null ? stageSupplier.get() : null;
        System.out.println("[AuctionEndedHandler] showToast() — stage=" + stage
                + " | winnerName='" + data.winnerName()
                + "' | itemName='" + data.itemName()
                + "' | finalPrice=" + data.finalPrice());

        if (stage == null) {
            System.err.println("[AuctionEndedHandler] stage == null, không hiển thị toast.");
            return;
        }

        String myName = UserSession.getInstance().getUsername();
        boolean hasWinner = data.winnerName() != null && !data.winnerName().isBlank();
        boolean iAmWinner = hasWinner && data.winnerName().equals(myName);

        if (!hasWinner) {
            // Phiên hủy — không có ai bid
            ToastNotification.info(stage,
                    "📭 Phiên đấu giá đã hủy",
                    String.format("Sản phẩm \"%s\" không có người tham gia.", data.itemName()));

        } else if (iAmWinner) {
            // Mình là người thắng
            ToastNotification.win(stage, data.itemName(), data.finalPrice());

        } else {
            // Có winner nhưng không phải mình
            ToastNotification.lose(stage, data.itemName());
        }
    }

    private String safeStr(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        return obj.get(key).getAsString();
    }

    private double safeDouble(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return 0;
        try { return obj.get(key).getAsDouble(); } catch (Exception e) { return 0; }
    }

    private record AuctionEndedData(
            String winnerName,
            String winnerId,
            String itemId,
            String itemName,
            double finalPrice,
            String message
    ) {}
}
