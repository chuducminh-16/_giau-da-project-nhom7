package com.auction.client.utils;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ToastNotification — hiện thông báo góc phải dưới màn hình.
 *
 * Cách dùng (chỉ cần gọi static method, không cần new):
 *
 *   // Khi có bid mới (trong HomeController.handleServerMessage):
 *   ToastNotification.bid(stage, "minh_dz", "bmw", 105_000);
 *
 *   // Thông báo thắng phiên:
 *   ToastNotification.win(stage, "bmw", 105_000);
 *
 *   // Thông báo thua phiên:
 *   ToastNotification.lose(stage, "bmw");
 *
 *   // Thông báo tùy chỉnh:
 *   ToastNotification.show(stage, "🔔", "Tiêu đề", "Nội dung", ToastType.INFO);
 *
 * KHÔNG cần sửa bất kỳ file nào khác để tạo toast.
 * Chỉ cần gọi ToastNotification.xxx() từ bất kỳ Controller nào.
 *
 * Để trigger từ HomeController khi nhận BID_UPDATE, thêm 1 dòng:
 *   ToastNotification.bid(primaryStage, bidderName, productName, newBid);
 * vào trong case "BID_UPDATE" của handleServerMessage().
 *
 * Lấy Stage từ bất kỳ Node nào:
 *   Stage stage = (Stage) anyNode.getScene().getWindow();
 */
public class ToastNotification {

    // ── Loại toast ────────────────────────────────────────────────────────
    public enum ToastType {
        BID,    // xanh dương — có bid mới
        WIN,    // xanh lá   — thắng phiên
        LOSE,   // đỏ        — thua phiên
        INFO,   // xám       — thông tin chung
        WARN    // cam        — cảnh báo
    }

    // ── Config ────────────────────────────────────────────────────────────
    private static final int    TOAST_WIDTH      = 320;
    private static final int    TOAST_HEIGHT     = 72;
    private static final int    TOAST_GAP        = 10;   // khoảng cách giữa các toast
    private static final int    MARGIN_RIGHT     = 16;   // cách mép phải màn hình
    private static final int    MARGIN_BOTTOM    = 48;   // cách mép dưới màn hình (tránh taskbar)
    private static final double DISPLAY_SECONDS  = 5.0;  // thời gian hiển thị
    private static final double FADE_SECONDS     = 0.4;  // thời gian fade in/out

    // ── Queue quản lý toast đang hiển thị ────────────────────────────────
    // Dùng LinkedList để xếp chồng: toast mới nhất ở dưới cùng (gần taskbar)
    // toast cũ hơn dần dần lên trên
    private static final LinkedList<Popup> activeToasts = new LinkedList<>();

    // ── Static API ────────────────────────────────────────────────────────

    /**
     * Toast khi có bid mới.
     * @param stage       Stage hiện tại (lấy từ node.getScene().getWindow())
     * @param bidderName  Tên người đặt giá
     * @param itemName    Tên sản phẩm
     * @param amount      Số tiền đặt giá
     */
    public static void bid(Stage stage, String bidderName, String itemName, double amount) {
        String title   = "🔔 Đặt giá mới!";
        String content = String.format("%s vừa đặt %,.0f VNĐ\ncho \"%s\"",
                bidderName, amount, itemName);
        show(stage, title, content, ToastType.BID);
    }

    /**
     * Toast khi thắng phiên.
     */
    public static void win(Stage stage, String itemName, double finalPrice) {
        String title   = "🏆 Chúc mừng! Bạn đã thắng!";
        String content = String.format("Sản phẩm \"%s\"\nGiá cuối: %,.0f VNĐ",
                itemName, finalPrice);
        show(stage, title, content, ToastType.WIN);
    }

    /**
     * Toast khi thua phiên.
     */
    public static void lose(Stage stage, String itemName) {
        String title   = "❌ Phiên đấu giá kết thúc";
        String content = String.format("Bạn đã thua phiên \"%s\".\nChúc may mắn lần sau!", itemName);
        show(stage, title, content, ToastType.LOSE);
    }

    /**
     * Toast thông tin chung.
     */
    public static void info(Stage stage, String title, String content) {
        show(stage, title, content, ToastType.INFO);
    }

    /**
     * Toast cảnh báo.
     */
    public static void warn(Stage stage, String title, String content) {
        show(stage, title, content, ToastType.WARN);
    }

    /**
     * Hiển thị toast tùy chỉnh.
     * Thread-safe: có thể gọi từ bất kỳ thread nào.
     */
    public static void show(Stage stage, String title, String content, ToastType type) {
        Platform.runLater(() -> showOnFxThread(stage, title, content, type));
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private static void showOnFxThread(Stage stage, String title,
                                       String content, ToastType type) {
        if (stage == null || !stage.isShowing()) return;

        // Tạo popup
        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);

        // Layout
        VBox card = buildCard(title, content, type);
        popup.getContent().add(card);

        // Tính vị trí
        Screen screen      = Screen.getPrimary();
        double screenW     = screen.getVisualBounds().getWidth();
        double screenH     = screen.getVisualBounds().getHeight();
        double x           = screenW - TOAST_WIDTH - MARGIN_RIGHT;
        double baseY       = screenH - TOAST_HEIGHT - MARGIN_BOTTOM;

        // Đẩy các toast cũ lên trên để nhường chỗ cho toast mới
        shiftExistingToastsUp();

        // Vị trí toast mới = sát đáy
        double y = baseY - activeToasts.size() * (TOAST_HEIGHT + TOAST_GAP);

        popup.show(stage, x, y);
        activeToasts.addLast(popup);

        // Slide in từ phải
        card.setTranslateX(TOAST_WIDTH + MARGIN_RIGHT);
        TranslateTransition slideIn = new TranslateTransition(
                Duration.millis(300), card);
        slideIn.setToX(0);
        slideIn.play();

        // Fade in
        card.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(
                Duration.seconds(FADE_SECONDS), card);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        // Timer tự đóng sau DISPLAY_SECONDS
        Timeline autoClose = new Timeline(
                new KeyFrame(Duration.seconds(DISPLAY_SECONDS),
                        e -> dismissToast(popup, card))
        );
        autoClose.play();

        // Click để đóng sớm
        card.setOnMouseClicked(e -> {
            autoClose.stop();
            dismissToast(popup, card);
        });
    }

    /** Đóng 1 toast với animation fade out. */
    private static void dismissToast(Popup popup, VBox card) {
        if (!popup.isShowing()) return;

        FadeTransition fadeOut = new FadeTransition(
                Duration.seconds(FADE_SECONDS), card);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            popup.hide();
            activeToasts.remove(popup);
            // Sau khi đóng, hạ các toast còn lại xuống
            recalculatePositions();
        });
        fadeOut.play();
    }

    /** Đẩy tất cả toast đang hiển thị lên trên 1 bậc (nhường chỗ cho toast mới). */
    private static void shiftExistingToastsUp() {
        // activeToasts[0] = cũ nhất (trên cùng), [last] = mới nhất (dưới cùng)
        // Khi thêm toast mới vào bottom, các toast cũ cần lên 1 bậc
        for (int i = 0; i < activeToasts.size(); i++) {
            Popup p = activeToasts.get(i);
            if (!p.isShowing()) continue;

            Screen screen  = Screen.getPrimary();
            double screenH = screen.getVisualBounds().getHeight();
            double newY    = screenH - TOAST_HEIGHT - MARGIN_BOTTOM
                           - (activeToasts.size() - i) * (TOAST_HEIGHT + TOAST_GAP);

            // Animate lên trên
            if (!p.getContent().isEmpty() && p.getContent().get(0) instanceof VBox card) {
                TranslateTransition move = new TranslateTransition(
                        Duration.millis(200), card);
                move.setByY(-(TOAST_HEIGHT + TOAST_GAP));
                move.play();
            }
            p.setY(newY);
        }
    }

    /** Tính lại vị trí tất cả toast sau khi đóng 1 cái. */
    private static void recalculatePositions() {
        Screen screen  = Screen.getPrimary();
        double screenH = screen.getVisualBounds().getHeight();
        double screenW = screen.getVisualBounds().getWidth();

        for (int i = 0; i < activeToasts.size(); i++) {
            Popup p = activeToasts.get(i);
            if (!p.isShowing()) continue;

            // index 0 = cũ nhất (trên cùng)
            // index size-1 = mới nhất (dưới cùng, sát taskbar)
            double newY = screenH - TOAST_HEIGHT - MARGIN_BOTTOM
                        - (activeToasts.size() - 1 - i) * (TOAST_HEIGHT + TOAST_GAP);
            double newX = screenW - TOAST_WIDTH - MARGIN_RIGHT;

            if (!p.getContent().isEmpty() && p.getContent().get(0) instanceof VBox card) {
                TranslateTransition move = new TranslateTransition(
                        Duration.millis(200), card);
                // Reset translateX/Y về 0 rồi set vị trí qua popup.setX/Y
                card.setTranslateX(0);
                move.setByY(0);
                move.play();
            }
            p.setX(newX);
            p.setY(newY);
        }
    }

    // ── Build UI ──────────────────────────────────────────────────────────

    private static VBox buildCard(String title, String content, ToastType type) {
        // Màu theo loại
        String accentColor = switch (type) {
            case BID  -> "#4299e1"; // xanh dương
            case WIN  -> "#38a169"; // xanh lá
            case LOSE -> "#e53e3e"; // đỏ
            case WARN -> "#dd6b20"; // cam
            default   -> "#718096"; // xám
        };

        String icon = switch (type) {
            case BID  -> "🔔";
            case WIN  -> "🏆";
            case LOSE -> "❌";
            case WARN -> "⚠";
            default   -> "ℹ";
        };

        // Card ngoài
        VBox card = new VBox();
        card.setPrefWidth(TOAST_WIDTH);
        card.setMinWidth(TOAST_WIDTH);
        card.setMaxWidth(TOAST_WIDTH);
        card.setStyle(
                "-fx-background-color: #1a1f2e;" +
                "-fx-background-radius: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 16, 0, 0, 4);" +
                "-fx-border-color: " + accentColor + ";" +
                "-fx-border-width: 0 0 0 4;" +    // đường kẻ màu bên trái
                "-fx-border-radius: 10;" +
                "-fx-cursor: hand;"
        );
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setSpacing(4);

        // Row trên: icon + title + nút đóng
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle(
                "-fx-text-fill: white;" +
                "-fx-font-size: 13;" +
                "-fx-font-weight: bold;"
        );
        titleLabel.setMaxWidth(TOAST_WIDTH - 80);

        // Nút X
        Label closeBtn = new Label("✕");
        closeBtn.setStyle(
                "-fx-text-fill: #718096;" +
                "-fx-font-size: 11;" +
                "-fx-cursor: hand;"
        );
        // Đẩy close button sang phải
        HBox spacer = new HBox();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        topRow.getChildren().addAll(iconLabel, titleLabel, spacer, closeBtn);

        // Row dưới: nội dung
        Label contentLabel = new Label(content);
        contentLabel.setStyle(
                "-fx-text-fill: #a0aec0;" +
                "-fx-font-size: 12;" +
                "-fx-wrap-text: true;"
        );
        contentLabel.setTextAlignment(TextAlignment.LEFT);
        contentLabel.setMaxWidth(TOAST_WIDTH - 32);
        contentLabel.setWrapText(true);

        // Progress bar (đếm ngược 5s)
        StackPane progressBar = buildProgressBar(accentColor);

        card.getChildren().addAll(topRow, contentLabel, progressBar);
        return card;
    }

    /** Thanh progress bar đếm ngược 5 giây. */
    private static StackPane buildProgressBar(String color) {
        // Background
        javafx.scene.shape.Rectangle bg = new javafx.scene.shape.Rectangle(
                TOAST_WIDTH - 32, 3);
        bg.setFill(javafx.scene.paint.Color.web("#2d3748"));
        bg.setArcWidth(3);
        bg.setArcHeight(3);

        // Foreground (shrinks from right to left)
        javafx.scene.shape.Rectangle fg = new javafx.scene.shape.Rectangle(
                TOAST_WIDTH - 32, 3);
        fg.setFill(javafx.scene.paint.Color.web(color));
        fg.setArcWidth(3);
        fg.setArcHeight(3);

        // Animate shrink
        Timeline shrink = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new javafx.animation.KeyValue(
                                fg.widthProperty(), TOAST_WIDTH - 32)),
                new KeyFrame(Duration.seconds(DISPLAY_SECONDS),
                        new javafx.animation.KeyValue(
                                fg.widthProperty(), 0))
        );
        shrink.play();

        StackPane bar = new StackPane(bg, fg);
        StackPane.setAlignment(fg, Pos.CENTER_LEFT);
        StackPane.setAlignment(bg, Pos.CENTER_LEFT);
        bar.setMaxWidth(TOAST_WIDTH - 32);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(bar, new Insets(4, 0, 0, 0));
        return bar;
    }
}
