package com.auction.client.handler.livebidding;

import com.auction.client.controller.LiveBiddingController;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.AutoBidSession;
import com.auction.client.session.UserSession;
import com.auction.client.utils.ToastNotification;
import com.auction.shared.model.Entity.Item.Item;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.Map;

/**
 * THƯ MỤC: client.handler
 * NHIỆM VỤ: Quản lý toàn bộ trạng thái, kiểm tra dữ liệu đầu vào và thực thi
 * logic đấu giá tự động (Auto-bid) theo thời gian thực mà không làm nghẽn UI chính.
 */
public class AutoBidHandler {

    private final LiveBiddingController mainController;

    private boolean autoBidActive   = false;
    private double  autoBidMaxPrice = 0;

    private String pendingAutoBidTriggerId   = null;
    private String pendingAutoBidTriggerName = null;

    public AutoBidHandler(LiveBiddingController mainController) {
        this.mainController = mainController;
    }

    // ── KHÔI PHỤC PHIÊN ────────────────────────────────────────────────────

    public void restoreSession(String itemId, double lastKnownBid) {
        String myUserId = UserSession.getInstance().getUserId();
        AutoBidSession session = AutoBidSession.getInstance();

        if (session.isActiveForProduct(myUserId, itemId)) {
            this.autoBidActive   = true;
            this.autoBidMaxPrice = session.getMaxPrice();
            mainController.setLatestBid(session.getLastKnownBid());

            if (mainController.getTxtMaxBid() != null) {
                mainController.getTxtMaxBid().setText(
                        String.format("%,.0f", autoBidMaxPrice).replace(',', '.'));
                mainController.getTxtMaxBid().setDisable(true);
            }

            // Khôi phục field bước giá riêng
            if (mainController.getTxtAutoBidIncrement() != null) {
                double savedIncrement = session.getBidIncrement();
                if (savedIncrement > 0) {
                    mainController.getTxtAutoBidIncrement().setText(
                            String.format("%,.0f", savedIncrement).replace(',', '.'));
                }
                mainController.getTxtAutoBidIncrement().setDisable(true);
            }

            if (mainController.getBtnAutoBid() != null) {
                updateButtonToActiveStyle(mainController.getBtnAutoBid());
            }
            setAutoBidStatusBadge(true);

            if (mainController.getHboxAutoBidInfo() != null) {
                mainController.getHboxAutoBidInfo().setVisible(true);
                mainController.getHboxAutoBidInfo().setManaged(true);
            }
            if (mainController.getLblAutoBidInfo() != null) {
                mainController.getLblAutoBidInfo().setText(String.format(
                        "Tối đa: %,.0f VNĐ  —  Đã tự động khôi phục phiên cũ", autoBidMaxPrice));
            }
            mainController.addLog(String.format(
                    "⚡ Auto-bid đã được khôi phục — mức tối đa %,.0f VNĐ", autoBidMaxPrice));
        }
    }

    // ── TOGGLE BẬT / TẮT ───────────────────────────────────────────────────

    public void toggleAutoBid() {
        if (autoBidActive) {
            deactivateAutoBid();
            mainController.addLog("⚡ Đã hủy kích hoạt tính năng Auto-bid.");
        } else {
            if (!validateAndActivateAutoBid()) return;
            mainController.addLog(String.format(
                    "⚡ Kích hoạt Auto-bid thành công — Tối đa: %,.0f VNĐ", autoBidMaxPrice));
        }
    }

    // ── KIỂM TRA & KÍCH HOẠT ───────────────────────────────────────────────

    private boolean validateAndActivateAutoBid() {
        TextField txtMaxBid       = mainController.getTxtMaxBid();
        // ĐỌC FIELD MỚI: bước giá riêng của bidder
        TextField txtIncrement    = mainController.getTxtAutoBidIncrement();

        if (txtMaxBid == null || txtIncrement == null) return false;

        // Validate giá tối đa
        String raw = txtMaxBid.getText().trim().replaceAll("\\.", "");
        if (raw.isEmpty()) {
            mainController.addLog("❌ Vui lòng nhập số tiền tối đa bạn muốn trả.");
            flashInputError(txtMaxBid);
            return false;
        }

        // Validate bước giá riêng
        String rawIncrement = txtIncrement.getText().trim().replaceAll("\\.", "");
        if (rawIncrement.isEmpty()) {
            mainController.addLog("❌ Vui lòng nhập bước giá tự động của bạn.");
            flashInputError(txtIncrement);
            return false;
        }

        double maxPrice;
        double userIncrement;
        try {
            maxPrice      = Double.parseDouble(raw);
            userIncrement = Double.parseDouble(rawIncrement);
        } catch (NumberFormatException e) {
            mainController.addLog("❌ Định dạng số không hợp lệ. Vui lòng nhập số nguyên!");
            flashInputError(txtMaxBid);
            return false;
        }

        if (maxPrice <= 0) {
            mainController.addLog("❌ Số tiền tối đa phải lớn hơn 0.");
            flashInputError(txtMaxBid);
            return false;
        }

        // Validate bước giá > 0
        if (userIncrement <= 0) {
            mainController.addLog("❌ Bước giá phải lớn hơn 0.");
            flashInputError(txtIncrement);
            return false;
        }

        Item currentItem = mainController.getCurrentItem();
        double currentBid = Math.max(
                mainController.getLatestBid(),
                currentItem != null ? currentItem.getCurrentBid() : 0);

        if (maxPrice <= currentBid) {
            mainController.addLog(String.format(
                    "❌ Giá tối đa (%,.0f) phải lớn hơn giá hiện tại (%,.0f).",
                    maxPrice, currentBid));
            flashInputError(txtMaxBid);
            return false;
        }

        // Lưu trạng thái
        this.autoBidMaxPrice = maxPrice;
        this.autoBidActive   = true;

        // Truyền userIncrement (bước giá RIÊNG của bidder) vào Session
        AutoBidSession.getInstance().activate(
                UserSession.getInstance().getUserId(),
                mainController.getItemId(),
                autoBidMaxPrice,
                currentBid,
                userIncrement   // ← BƯỚC GIÁ RIÊNG, không lấy từ Item nữa
        );

        // Gửi lên server kèm increment riêng của bidder
        NetworkClient.getInstance().send(new Message("SET_AUTO_BID",
                mainController.getGson().toJson(Map.of(
                        "productId", mainController.getItemId(),
                        "maxBid",    autoBidMaxPrice,
                        "increment", userIncrement   // ← GỬI ĐÚNG GIÁ TRỊ
                ))));

        // Khóa cả hai field
        txtMaxBid.setDisable(true);
        txtIncrement.setDisable(true);  // ← KHÓA FIELD MỚI

        updateButtonToActiveStyle(mainController.getBtnAutoBid());
        setAutoBidStatusBadge(true);

        if (mainController.getHboxAutoBidInfo() != null) {
            mainController.getHboxAutoBidInfo().setVisible(true);
            mainController.getHboxAutoBidInfo().setManaged(true);
        }
        if (mainController.getLblAutoBidInfo() != null) {
            mainController.getLblAutoBidInfo().setText(String.format(
                    "Tối đa: %,.0f VNĐ  —  Bước: %,.0f VNĐ  —  Đang canh giá...",
                    autoBidMaxPrice, userIncrement));
        }
        return true;
    }

    // ── TẮT AUTO-BID ───────────────────────────────────────────────────────

    public void deactivateAutoBid() {
        this.autoBidActive   = false;
        this.autoBidMaxPrice = 0;

        AutoBidSession.getInstance().clear();

        // Mở khóa cả hai field
        if (mainController.getTxtMaxBid()           != null) mainController.getTxtMaxBid().setDisable(false);
        if (mainController.getTxtAutoBidIncrement() != null) mainController.getTxtAutoBidIncrement().setDisable(false);  // ← MỞ KHÓA FIELD MỚI

        if (mainController.getBtnAutoBid() != null) {
            mainController.getBtnAutoBid().setText("⚡ Kích hoạt");
            mainController.getBtnAutoBid().setStyle(
                    "-fx-background-color: #d69e2e; -fx-text-fill: white;" +
                    "-fx-font-size: 13; -fx-font-weight: bold;" +
                    "-fx-background-radius: 6; -fx-cursor: hand;");
        }

        setAutoBidStatusBadge(false);

        if (mainController.getHboxAutoBidInfo() != null) {
            mainController.getHboxAutoBidInfo().setVisible(false);
            mainController.getHboxAutoBidInfo().setManaged(false);
        }

        this.pendingAutoBidTriggerId   = null;
        this.pendingAutoBidTriggerName = null;
    }

    // ── THỰC THI AUTO-BID ──────────────────────────────────────────────────

    public void handleAutoBidIfNeeded(String lastBidderId, String lastBidderName) {
        if (!autoBidActive) return;

        Item currentItem = mainController.getCurrentItem();
        if (currentItem == null) return;

        if (mainController.getSecondsRemaining() == 0) return;

        String myUserId   = UserSession.getInstance().getUserId();
        String myUsername = UserSession.getInstance().getUsername();

        boolean iAmLeading;
        if (lastBidderId != null && !lastBidderId.isBlank()) {
            iAmLeading = myUserId.equals(lastBidderId);
        } else {
            String cleanName = lastBidderName != null
                    ? lastBidderName.replace(" (auto)", "").trim() : "";
            iAmLeading = myUsername != null && myUsername.equals(cleanName);
        }

        if (iAmLeading) {
            if (mainController.getLblAutoBidInfo() != null) {
                mainController.getLblAutoBidInfo().setText(String.format(
                        "Tối đa: %,.0f VNĐ  —  Bạn đang dẫn đầu cuộc đua!", autoBidMaxPrice));
            }
            return;
        }

        // LẤY BƯỚC GIÁ TỪ SESSION (do bidder tự nhập), không lấy từ Item
        double increment = AutoBidSession.getInstance().getBidIncrement();
        if (increment <= 0) {
            // Fallback: nếu Session không có thì thử lấy từ Item
            if (currentItem.getBidIncrement() > 0) {
                increment = currentItem.getBidIncrement();
            } else {
                increment = 1000; // mức sàn cứu cánh
            }
        }

        double nextBidAmt = mainController.getLatestBid() + increment;

        if (nextBidAmt > autoBidMaxPrice) {
            mainController.addLog(String.format(
                    "⚡ Auto-bid dừng: Giá đề xuất (%,.0f) vượt giới hạn tối đa bạn đặt (%,.0f).",
                    nextBidAmt, autoBidMaxPrice));
            deactivateAutoBid();
            try {
                Stage stage = (Stage) mainController.getLblCountdown().getScene().getWindow();
                ToastNotification.warn(stage, "⚡ Auto-bid đã dừng",
                        String.format("Mức giá thị trường vượt quá số tiền %,.0f VNĐ giới hạn của bạn.",
                                autoBidMaxPrice));
            } catch (Exception ignored) {}
            return;
        }

        mainController.addLog(String.format(
                "🤖 Máy tính tự động đặt mức giá phản pháo: %,.0f VNĐ...", nextBidAmt));
        if (mainController.getLblAutoBidInfo() != null) {
            mainController.getLblAutoBidInfo().setText(String.format(
                    "Tối đa: %,.0f VNĐ  —  Vừa tự động đặt: %,.0f VNĐ",
                    autoBidMaxPrice, nextBidAmt));
        }

        NetworkClient.getInstance().send(new Message("PLACE_BID",
                mainController.getGson().toJson(Map.of(
                        "productId", currentItem.getId(),
                        "bidderId",  myUserId,
                        "amount",    nextBidAmt
                ))));
    }

    // ── UI HELPER ─────────────────────────────────────────────────────────

    private void updateButtonToActiveStyle(Button btn) {
        if (btn == null) return;
        btn.setText("⏹ Dừng Auto-bid");
        btn.setStyle("-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                     "-fx-font-size: 13; -fx-font-weight: bold;" +
                     "-fx-background-radius: 6; -fx-cursor: hand;");
    }

    private void setAutoBidStatusBadge(boolean active) {
        Label lblStatus = mainController.getLblAutoBidStatus();
        if (lblStatus == null) return;
        if (active) {
            lblStatus.setText("● BẬT");
            lblStatus.setStyle("-fx-text-fill: #68d391; -fx-font-size: 11; -fx-font-weight: bold;" +
                               "-fx-background-color: #1c4532; -fx-background-radius: 20; -fx-padding: 3 10;");
        } else {
            lblStatus.setText("● TẮT");
            lblStatus.setStyle("-fx-text-fill: #718096; -fx-font-size: 11; -fx-font-weight: bold;" +
                               "-fx-background-color: #2d3748; -fx-background-radius: 20; -fx-padding: 3 10;");
        }
    }

    private void flashInputError(TextField field) {
        String original = field.getStyle();
        field.setStyle(original + "; -fx-border-color: #e53e3e; -fx-border-width: 2;");
        Timeline reset = new Timeline(
                new KeyFrame(Duration.seconds(1.5), e -> field.setStyle(original)));
        reset.setCycleCount(1);
        reset.play();
    }

    // ── GETTERS / SETTERS ─────────────────────────────────────────────────

    public boolean isAutoBidActive()                          { return autoBidActive; }
    public String  getPendingAutoBidTriggerId()               { return pendingAutoBidTriggerId; }
    public void    setPendingAutoBidTriggerId(String id)      { this.pendingAutoBidTriggerId = id; }
    public String  getPendingAutoBidTriggerName()             { return pendingAutoBidTriggerName; }
    public void    setPendingAutoBidTriggerName(String name)  { this.pendingAutoBidTriggerName = name; }
}