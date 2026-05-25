package com.auction.client.utils;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.LinkedList;

/**
 * WinnerToastNotification — hiển thị thông báo kết thúc phiên đấu giá.
 *
 * ═══════════════════════════════════════════════════════════════════
 *  FILE MỚI — TÁCH RIÊNG từ ToastNotification.java
 *
 *  Khác với ToastNotification (dùng cho bid thông thường):
 *    - Toast lớn hơn (400×100px), xuất hiện ở GIỮA màn hình phía trên
 *    - Animation: scale từ 0.5 → 1.0 kết hợp fade in (dramatic entrance)
 *    - 3 kiểu toast:
 *        winner()  → nền vàng gold, trophy icon — cho người thắng
 *        loser()   → nền đỏ tối, X icon           — cho người thua
 *        noWinner()→ nền xám, info icon            — phiên kết thúc không có bid
 *    - Hiển thị 8 giây (lâu hơn toast bid thường 5 giây)
 *    - Không xếp chồng như bid toast; chỉ hiện 1 toast winner tại 1 thời điểm
 *
 *  Cách dùng trong LiveBiddingController hoặc AuctionEndedHandler:
 *
 *    // Người chiến thắng:
 *    WinnerToastNotification.winner(stage, "duong", "cr7", 300_000);
 *
 *    // Người thua:
 *    WinnerToastNotification.loser(stage, "cr7", "duong", 300_000);
 *
 *    // Phiên kết thúc không ai bid:
 *    WinnerToastNotification.noWinner(stage, "cr7");
 *
 *  KHÔNG cần sửa bất kỳ file nào khác để dùng class này.
 * ═══════════════════════════════════════════════════════════════════
 */
public class WinnerToastNotification {

    // ── Config ────────────────────────────────────────────────────────────
    private static final int    TOAST_WIDTH      = 420;
    private static final int    TOAST_HEIGHT     = 110;
    private static final double DISPLAY_SECONDS  = 8.0;
    private static final double FADE_SECONDS     = 0.35;

    // Chỉ cho phép 1 winner toast tại 1 thời điểm
    private static Popup currentWinnerToast = null;

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Toast cho người THẮNG phiên đấu giá.
     *
     * @param stage      Stage hiện tại
     * @param winnerName Tên người thắng
     * @param itemName   Tên sản phẩm
     * @param finalPrice Giá cuối cùng
     */
    public static void winner(Stage stage, String winnerName, String itemName, double finalPrice) {
        String title   = "🏆 Chúc mừng! Bạn đã THẮNG!";
        String line1   = String.format("Sản phẩm: \"%s\"", itemName);
        String line2   = String.format("Giá cuối: %,.0f VNĐ", finalPrice);
        show(stage, title, line1, line2, ToastKind.WINNER);
    }

    /**
     * Toast cho người THUA phiên đấu giá.
     *
     * @param stage      Stage hiện tại
     * @param itemName   Tên sản phẩm
     * @param winnerName Tên người thắng (để biết ai đã thắng)
     * @param finalPrice Giá cuối cùng
     */
    public static void loser(Stage stage, String itemName, String winnerName, double finalPrice) {
        String title = "❌ Phiên đấu giá đã kết thúc";
        String line1 = String.format("Người thắng: %s", winnerName != null ? winnerName : "Không rõ");
        String line2 = String.format("Giá cuối: %,.0f VNĐ — Chúc may mắn lần sau!", finalPrice);
        show(stage, title, line1, line2, ToastKind.LOSER);
    }

    /**
     * Toast khi phiên kết thúc mà KHÔNG có ai đặt giá.
     *
     * @param stage    Stage hiện tại
     * @param itemName Tên sản phẩm
     */
    public static void noWinner(Stage stage, String itemName) {
        String title = "📭 Phiên đấu giá đã hủy";
        String line1 = String.format("Sản phẩm \"%s\" không có người tham gia.", itemName);
        String line2 = "Phiên đã bị hủy tự động.";
        show(stage, title, line1, line2, ToastKind.NO_WINNER);
    }

    /**
     * Toast thông tin kết thúc phiên (broadcast chung cho tất cả người xem).
     * Dùng khi server broadcast AUCTION_ENDED mà client không biết mình thắng hay thua.
     *
     * @param stage      Stage hiện tại
     * @param winnerName Tên người thắng (null nếu không có)
     * @param itemName   Tên sản phẩm
     * @param finalPrice Giá cuối (0 nếu không có bid)
     */
    public static void auctionEnded(Stage stage, String winnerName,
                                    String itemName, double finalPrice) {
        if (winnerName == null || winnerName.isBlank()) {
            noWinner(stage, itemName);
        } else {
            String title = "🏁 Kết quả đấu giá";
            String line1 = String.format("Sản phẩm: \"%s\"", itemName);
            String line2 = String.format("🏆 %s thắng với %,.0f VNĐ", winnerName, finalPrice);
            show(stage, title, line1, line2, ToastKind.ENDED);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private enum ToastKind { WINNER, LOSER, NO_WINNER, ENDED }

    private static void show(Stage stage, String title,
                              String line1, String line2, ToastKind kind) {
        Platform.runLater(() -> showOnFxThread(stage, title, line1, line2, kind));
    }

    private static void showOnFxThread(Stage stage, String title,
                                       String line1, String line2, ToastKind kind) {
        if (stage == null || !stage.isShowing()) return;

        // Đóng toast winner cũ nếu có
        if (currentWinnerToast != null && currentWinnerToast.isShowing()) {
            currentWinnerToast.hide();
        }

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);

        VBox card = buildCard(title, line1, line2, kind);
        popup.getContent().add(card);

        // Vị trí: giữa màn hình, 1/4 từ trên xuống
        Screen screen = Screen.getPrimary();
        double screenW = screen.getVisualBounds().getWidth();
        double screenH = screen.getVisualBounds().getHeight();
        double x = (screenW - TOAST_WIDTH) / 2.0;
        double y = screenH * 0.18; // 18% từ trên xuống

        // Scale + fade in từ tâm
        card.setOpacity(0);
        card.setScaleX(0.6);
        card.setScaleY(0.6);

        popup.show(stage, x, y);
        currentWinnerToast = popup;

        // Animation: parallel scale + fade
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(380), card);
        scaleIn.setToX(1.0);
        scaleIn.setToY(1.0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(380), card);
        fadeIn.setToValue(1.0);

        // Nhún nhẹ (overshoot)
        scaleIn.setInterpolator(javafx.animation.Interpolator.SPLINE(0.34, 1.56, 0.64, 1.0));

        ParallelTransition entrance = new ParallelTransition(scaleIn, fadeIn);
        entrance.play();

        // Shimmer animation cho WINNER
        if (kind == ToastKind.WINNER) {
            playShimmer(card);
        }

        // Auto-close sau DISPLAY_SECONDS
        Timeline autoClose = new Timeline(
                new KeyFrame(Duration.seconds(DISPLAY_SECONDS),
                        e -> dismissToast(popup, card))
        );
        autoClose.play();

        // Click để đóng
        card.setOnMouseClicked(e -> {
            autoClose.stop();
            dismissToast(popup, card);
        });
    }

    private static void dismissToast(Popup popup, VBox card) {
        if (!popup.isShowing()) return;

        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), card);
        fadeOut.setToValue(0);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(400), card);
        scaleOut.setToX(0.8);
        scaleOut.setToY(0.8);

        ParallelTransition exit = new ParallelTransition(fadeOut, scaleOut);
        exit.setOnFinished(e -> {
            popup.hide();
            if (currentWinnerToast == popup) currentWinnerToast = null;
        });
        exit.play();
    }

    /**
     * Shimmer animation: thanh sáng chạy ngang qua card (chỉ cho WINNER).
     */
    private static void playShimmer(VBox card) {
        // Dùng Timeline để delay shimmer lặp lại 2 lần
        Timeline shimmerLoop = new Timeline(
                new KeyFrame(Duration.millis(600),  e -> runOneShimmer(card)),
                new KeyFrame(Duration.millis(1800), e -> runOneShimmer(card))
        );
        shimmerLoop.setCycleCount(1);
        shimmerLoop.play();
    }

    private static void runOneShimmer(VBox card) {
        // Tạo thanh sáng trắng trong suốt
        Rectangle shimmerBar = new Rectangle(60, TOAST_HEIGHT);
        shimmerBar.setFill(Color.web("rgba(255,255,255,0.18)"));
        shimmerBar.setArcWidth(4);
        shimmerBar.setArcHeight(4);
        shimmerBar.setRotate(15); // nghiêng nhẹ

        // Dùng clip trên card để shimmer không tràn ra ngoài
        // (gắn shimmerBar như overlay tạm thời)
        StackPane overlay = new StackPane(shimmerBar);
        overlay.setMaxWidth(TOAST_WIDTH);
        overlay.setMaxHeight(TOAST_HEIGHT);
        overlay.setMouseTransparent(true);
        overlay.setStyle("-fx-background-color: transparent;");
        StackPane.setAlignment(shimmerBar, Pos.CENTER_LEFT);

        // Đặt shimmer ở đầu card rồi slide sang phải
        shimmerBar.setTranslateX(-80);
        card.getChildren().add(overlay);

        TranslateTransition slide = new TranslateTransition(Duration.millis(500), shimmerBar);
        slide.setToX(TOAST_WIDTH + 80);
        slide.setOnFinished(e -> card.getChildren().remove(overlay));
        slide.play();
    }

    // ── Build UI ──────────────────────────────────────────────────────────

    private static VBox buildCard(String title, String line1,
                                   String line2, ToastKind kind) {
        // Màu nền và accent theo loại
        String bgColor;
        String accentColor;
        String borderColor;

        switch (kind) {
            case WINNER -> {
                bgColor     = "#1a1506";  // nền đen ánh vàng
                accentColor = "#f6c90e";  // vàng gold
                borderColor = "#f6c90e";
            }
            case LOSER -> {
                bgColor     = "#1a0606";  // nền đen ánh đỏ
                accentColor = "#e53e3e";  // đỏ
                borderColor = "#e53e3e";
            }
            case NO_WINNER -> {
                bgColor     = "#111318";  // xám đen
                accentColor = "#718096";  // xám
                borderColor = "#4a5568";
            }
            default -> {  // ENDED
                bgColor     = "#0a1628";  // xanh navy đen
                accentColor = "#4299e1";  // xanh dương
                borderColor = "#4299e1";
            }
        }

        VBox card = new VBox(6);
        card.setPrefWidth(TOAST_WIDTH);
        card.setMinWidth(TOAST_WIDTH);
        card.setMaxWidth(TOAST_WIDTH);
        card.setPrefHeight(TOAST_HEIGHT);
        card.setPadding(new Insets(14, 18, 14, 18));
        card.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                "-fx-background-radius: 12;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 28, 0.1, 0, 6);" +
                "-fx-border-color: " + borderColor + ";" +
                "-fx-border-width: 1.5;" +
                "-fx-border-radius: 12;" +
                "-fx-cursor: hand;"
        );

        // Row 1: title + nút đóng
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
                "-fx-text-fill: " + accentColor + ";" +
                "-fx-font-size: 15;" +
                "-fx-font-weight: bold;"
        );
        titleLabel.setMaxWidth(TOAST_WIDTH - 60);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label closeBtn = new Label("✕");
        closeBtn.setStyle(
                "-fx-text-fill: #4a5568;" +
                "-fx-font-size: 11;" +
                "-fx-cursor: hand;"
        );

        topRow.getChildren().addAll(titleLabel, spacer, closeBtn);

        // Row 2: line1 (sản phẩm)
        Label lblLine1 = new Label(line1);
        lblLine1.setStyle(
                "-fx-text-fill: #e2e8f0;" +
                "-fx-font-size: 12.5;" +
                "-fx-wrap-text: true;"
        );
        lblLine1.setMaxWidth(TOAST_WIDTH - 36);
        lblLine1.setWrapText(true);

        // Row 3: line2 (giá / kết quả)
        Label lblLine2 = new Label(line2);
        lblLine2.setStyle(
                "-fx-text-fill: " + accentColor + ";" +
                "-fx-font-size: 12;" +
                "-fx-font-weight: bold;" +
                "-fx-wrap-text: true;"
        );
        lblLine2.setMaxWidth(TOAST_WIDTH - 36);
        lblLine2.setWrapText(true);

        // Progress bar đếm ngược
        StackPane progressBar = buildProgressBar(accentColor);

        card.getChildren().addAll(topRow, lblLine1, lblLine2, progressBar);
        return card;
    }

    private static StackPane buildProgressBar(String color) {
        double barWidth = TOAST_WIDTH - 36;

        Rectangle bg = new Rectangle(barWidth, 3);
        bg.setFill(Color.web("#2d3748"));
        bg.setArcWidth(3);
        bg.setArcHeight(3);

        Rectangle fg = new Rectangle(barWidth, 3);
        fg.setFill(Color.web(color));
        fg.setArcWidth(3);
        fg.setArcHeight(3);

        Timeline shrink = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(fg.widthProperty(), barWidth)),
                new KeyFrame(Duration.seconds(DISPLAY_SECONDS),
                        new KeyValue(fg.widthProperty(), 0))
        );
        shrink.play();

        StackPane bar = new StackPane(bg, fg);
        StackPane.setAlignment(fg, Pos.CENTER_LEFT);
        StackPane.setAlignment(bg, Pos.CENTER_LEFT);
        bar.setMaxWidth(barWidth);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(bar, new Insets(2, 0, 0, 0));
        return bar;
    }
}
