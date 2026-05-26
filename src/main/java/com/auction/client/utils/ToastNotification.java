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

/**
 * ToastNotification — Hiện thông báo đặt giá nhanh góc phải màn hình.
 */
public class ToastNotification {

    public enum ToastType {
        BID,    // xanh dương — có bid mới
        INFO,   // xám       — thông tin chung
        WARN    // cam       — cảnh báo
    }

    private static final int    TOAST_WIDTH      = 320;
    private static final int    TOAST_HEIGHT     = 72;
    private static final int    TOAST_GAP        = 10;
    private static final int    MARGIN_RIGHT     = 16;
    private static final int    MARGIN_BOTTOM    = 48;
    private static final double DISPLAY_SECONDS  = 5.0;
    private static final double FADE_SECONDS     = 0.4;

    private static final LinkedList<Popup> activeToasts = new LinkedList<>();

    public static void bid(Stage stage, String bidderName, String itemName, double amount) {
        String title   = "🔔 Đặt giá mới!";
        String content = String.format("%s vừa đặt %,.0f VNĐ\ncho \"%s\"", bidderName, amount, itemName);
        show(stage, title, content, ToastType.BID);
    }

    public static void info(Stage stage, String title, String content) {
        show(stage, title, content, ToastType.INFO);
    }

    public static void warn(Stage stage, String title, String content) {
        show(stage, title, content, ToastType.WARN);
    }

    public static void show(Stage stage, String title, String content, ToastType type) {
        Platform.runLater(() -> showOnFxThread(stage, title, content, type));
    }

    private static void showOnFxThread(Stage stage, String title, String content, ToastType type) {
        if (stage == null || !stage.isShowing()) return;

        Popup popup = new Popup();
        popup.setAutoFix(false);
        popup.setAutoHide(false);

        VBox card = buildCard(title, content, type);
        popup.getContent().add(card);

        Screen screen      = Screen.getPrimary();
        double screenW     = screen.getVisualBounds().getWidth();
        double screenH     = screen.getVisualBounds().getHeight();
        double x           = screenW - TOAST_WIDTH - MARGIN_RIGHT;
        double baseY       = screenH - TOAST_HEIGHT - MARGIN_BOTTOM;

        shiftExistingToastsUp();

        double y = baseY - activeToasts.size() * (TOAST_HEIGHT + TOAST_GAP);

        popup.show(stage, x, y);
        activeToasts.addLast(popup);

        card.setTranslateX(TOAST_WIDTH + MARGIN_RIGHT);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), card);
        slideIn.setToX(0);
        slideIn.play();

        card.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.seconds(FADE_SECONDS), card);
        fadeIn.setToValue(1.0);
        fadeIn.play();

        Timeline autoClose = new Timeline(
                new KeyFrame(Duration.seconds(DISPLAY_SECONDS), e -> dismissToast(popup, card))
        );
        autoClose.play();

        card.setOnMouseClicked(e -> {
            autoClose.stop();
            dismissToast(popup, card);
        });
    }

    private static void dismissToast(Popup popup, VBox card) {
        if (!popup.isShowing()) return;

        FadeTransition fadeOut = new FadeTransition(Duration.seconds(FADE_SECONDS), card);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            popup.hide();
            activeToasts.remove(popup);
            recalculatePositions();
        });
        fadeOut.play();
    }

    private static void shiftExistingToastsUp() {
        for (int i = 0; i < activeToasts.size(); i++) {
            Popup p = activeToasts.get(i);
            if (!p.isShowing()) continue;

            Screen screen  = Screen.getPrimary();
            double screenH = screen.getVisualBounds().getHeight();
            double newY    = screenH - TOAST_HEIGHT - MARGIN_BOTTOM
                           - (activeToasts.size() - i) * (TOAST_HEIGHT + TOAST_GAP);

            if (!p.getContent().isEmpty() && p.getContent().get(0) instanceof VBox card) {
                TranslateTransition move = new TranslateTransition(Duration.millis(200), card);
                move.setByY(-(TOAST_HEIGHT + TOAST_GAP));
                move.play();
            }
            p.setY(newY);
        }
    }

    private static void recalculatePositions() {
        Screen screen  = Screen.getPrimary();
        double screenH = screen.getVisualBounds().getHeight();
        double screenW = screen.getVisualBounds().getWidth();

        for (int i = 0; i < activeToasts.size(); i++) {
            Popup p = activeToasts.get(i);
            if (!p.isShowing()) continue;

            double newY = screenH - TOAST_HEIGHT - MARGIN_BOTTOM
                        - (activeToasts.size() - 1 - i) * (TOAST_HEIGHT + TOAST_GAP);
            double newX = screenW - TOAST_WIDTH - MARGIN_RIGHT;

            if (!p.getContent().isEmpty() && p.getContent().get(0) instanceof VBox card) {
                TranslateTransition move = new TranslateTransition(Duration.millis(200), card);
                card.setTranslateX(0);
                move.setByY(0);
                move.play();
            }
            p.setX(newX);
            p.setY(newY);
        }
    }

    private static VBox buildCard(String title, String content, ToastType type) {
        String accentColor = switch (type) {
            case BID  -> "#4299e1";
            case WARN -> "#dd6b20";
            default   -> "#718096";
        };

        String icon = switch (type) {
            case BID  -> "🔔";
            case WARN -> "⚠";
            default   -> "ℹ";
        };

        VBox card = new VBox();
        card.setPrefWidth(TOAST_WIDTH);
        card.setMinWidth(TOAST_WIDTH);
        card.setMaxWidth(TOAST_WIDTH);
        card.setStyle(
                "-fx-background-color: #1a1f2e;" +
                "-fx-background-radius: 10;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 16, 0, 0, 4);" +
                "-fx-border-color: " + accentColor + ";" +
                "-fx-border-width: 0 0 0 4;" +   
                "-fx-border-radius: 10;" +
                "-fx-cursor: hand;"
        );
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setSpacing(4);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 16;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13; -fx-font-weight: bold;");
        titleLabel.setMaxWidth(TOAST_WIDTH - 80);

        Label closeBtn = new Label("✕");
        closeBtn.setStyle("-fx-text-fill: #718096; -fx-font-size: 11; -fx-cursor: hand;");
        HBox spacer = new HBox();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        topRow.getChildren().addAll(iconLabel, titleLabel, spacer, closeBtn);

        Label contentLabel = new Label(content);
        contentLabel.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 12; -fx-wrap-text: true;");
        contentLabel.setTextAlignment(TextAlignment.LEFT);
        contentLabel.setMaxWidth(TOAST_WIDTH - 32);
        contentLabel.setWrapText(true);

        StackPane progressBar = buildProgressBar(accentColor);

        card.getChildren().addAll(topRow, contentLabel, progressBar);
        return card;
    }

    private static StackPane buildProgressBar(String color) {
        javafx.scene.shape.Rectangle bg = new javafx.scene.shape.Rectangle(TOAST_WIDTH - 32, 3);
        bg.setFill(javafx.scene.paint.Color.web("#2d3748"));
        bg.setArcWidth(3);
        bg.setArcHeight(3);

        javafx.scene.shape.Rectangle fg = new javafx.scene.shape.Rectangle(TOAST_WIDTH - 32, 3);
        fg.setFill(javafx.scene.paint.Color.web(color));
        fg.setArcWidth(3);
        fg.setArcHeight(3);

        Timeline shrink = new Timeline(
                new KeyFrame(Duration.ZERO, new javafx.animation.KeyValue(fg.widthProperty(), TOAST_WIDTH - 32)),
                new KeyFrame(Duration.seconds(DISPLAY_SECONDS), new javafx.animation.KeyValue(fg.widthProperty(), 0))
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