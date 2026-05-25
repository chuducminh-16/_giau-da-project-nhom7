package com.auction.client.controller;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * SnipeGuardLiveBiddingController
 * ═══════════════════════════════════════════════════════════════════
 *  FILE MỚI — thay thế LiveBiddingController.java gốc.
 *
 *  Bổ sung so với bản gốc:
 *    1. Xử lý message AUCTION_EXTENDED hoàn chỉnh:
 *       - Restart đồng hồ đếm ngược với thời gian mới
 *       - Đổi màu countdown label sang vàng rồi về đỏ để báo hiệu
 *       - Flash animation để user chú ý
 *       - Hiển thị banner thông báo gia hạn
 *
 *    2. Countdown label thay đổi màu theo mức độ:
 *       - Xanh lá  : > 5 phút  (bình thường)
 *       - Vàng     : 1~5 phút  (cảnh báo)
 *       - Đỏ       : < 1 phút  (nguy hiểm, nhấp nháy)
 *       - Cam sáng : ngay lúc gia hạn (báo hiệu SnipeGuard vừa kích hoạt)
 *
 *    3. Log rõ ràng hơn trong listNotifications
 *
 *  FXML dùng chung với live-bidding-view.fxml gốc (giữ nguyên fx:id).
 *  Chỉ cần đổi fx:controller trong FXML:
 *    từ: com.auction.client.controller.LiveBiddingController
 *    sang: com.auction.client.controller.SnipeGuardLiveBiddingController
 * ═══════════════════════════════════════════════════════════════════
 */
public class SnipeGuardLiveBiddingController implements Initializable {

    // ── FXML bindings (giữ nguyên fx:id từ live-bidding-view.fxml) ───────
    @FXML private Label     lblCountdown;
    @FXML private Label     lblProductName;
    @FXML private Label     lblCurrentBid;
    @FXML private TextField txtBidAmount;
    @FXML private LineChart<String, Number> priceChart;

    @FXML private TableView<BidItem>          tableBidHistory;
    @FXML private TableColumn<BidItem,String> colTime;
    @FXML private TableColumn<BidItem,String> colUser;
    @FXML private TableColumn<BidItem,String> colAmount;

    @FXML private ListView<String> listNotifications;

    // ── State ─────────────────────────────────────────────────────────────
    private Item     currentItem;
    private String   itemId;
    private XYChart.Series<String, Number> priceSeries;

    /** Timeline cho đồng hồ đếm ngược (1 tick = 1 giây) */
    private Timeline countdownTimeline;

    /** Số giây còn lại — được cập nhật mỗi giây và khi nhận AUCTION_EXTENDED */
    private long secondsRemaining;

    /** Đang trong trạng thái flash gia hạn hay không */
    private boolean isFlashing = false;

    // Màu sắc cho countdown
    private static final String COLOR_NORMAL  = "-fx-text-fill:#68d391; -fx-font-size:22; -fx-font-weight:bold;";
    private static final String COLOR_WARNING = "-fx-text-fill:#d69e2e; -fx-font-size:22; -fx-font-weight:bold;";
    private static final String COLOR_DANGER  = "-fx-text-fill:#fc8181; -fx-font-size:22; -fx-font-weight:bold;";
    private static final String COLOR_EXTENDED = "-fx-text-fill:#f6ad55; -fx-font-size:24; -fx-font-weight:bold;";
    private static final String COLOR_ENDED   = "-fx-text-fill:#a0aec0; -fx-font-size:18; -fx-font-weight:bold;";

    private final ObservableList<BidItem> bidHistory    = FXCollections.observableArrayList();
    private final ObservableList<String>  notifications = FXCollections.observableArrayList();

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    // ─────────────────────────────────────────────────────────────────────
    //  Gson custom deserializer (giống gốc)
    // ─────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────
    //  initialize
    // ─────────────────────────────────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupChart();
        client.addListener(listener);

        itemId = SelectedProductSession.getInstance().getProductId();

        if (itemId != null) {
            addLog("🔌 Đang kết nối phòng đấu giá...");
            client.send(new Message("GET_PRODUCT_DETAIL",
                    gson.toJson(Map.of("itemId", itemId))));
        } else {
            addLog("❌ Không tìm thấy thông tin sản phẩm!");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  handleServerMessage — điểm vào xử lý tất cả message từ server
    // ─────────────────────────────────────────────────────────────────────
    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            // ── Nhận thông tin sản phẩm lần đầu ──────────────────────────
            case "PRODUCT_DETAIL_RESPONSE" -> {
                try {
                    JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                    if (!root.has("success") || !root.get("success").getAsBoolean()) {
                        Platform.runLater(() -> addLog("❌ Không tìm thấy sản phẩm!"));
                        return;
                    }
                    Item item = gson.fromJson(root.get("item"), Item.class);
                    if (item == null) return;

                    Platform.runLater(() -> {
                        currentItem = item;
                        populateProductInfo();
                        startCountdownFromItem();
                        client.send(new Message("WATCH_AUCTION",
                                gson.toJson(Map.of("auctionId", item.getId()))));
                        addChartPoint(item.getCurrentBid());
                        addLog("✅ Chào mừng vào phòng: " + item.getName());
                    });
                } catch (Exception e) {
                    System.err.println("[SnipeGuardLive] Lỗi parse PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
                }
            }

            // ── Có bid mới ───────────────────────────────────────────────
            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId  = dto.get("productId").getAsString();
                    if (currentItem == null || !currentItem.getId().equals(productId)) return;

                    double newBid     = dto.get("newBid").getAsDouble();
                    String bidderName = dto.has("bidderName")
                            ? dto.get("bidderName").getAsString() : "Unknown";

                    Platform.runLater(() -> {
                        currentItem.setCurrentBid(newBid);
                        updateCurrentBidLabel(newBid);
                        String time = LocalTime.now().format(timeFmt);
                        bidHistory.add(0, new BidItem(time, bidderName,
                                String.format("%,.0f VNĐ", newBid)));
                        addChartPoint(newBid);
                        addLog(String.format("💰 %s vừa đặt %,.0f VNĐ", bidderName, newBid));
                    });
                } catch (Exception e) {
                    System.err.println("[SnipeGuardLive] Lỗi parse BID_UPDATE: " + e.getMessage());
                }
            }

            // ── Kết quả bid của chính mình ───────────────────────────────
            case "BID_RESULT" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    boolean success  = dto.get("success").getAsBoolean();
                    String  message  = dto.has("message") ? dto.get("message").getAsString() : "";
                    Platform.runLater(() ->
                            addLog(success ? "✅ Đặt giá thành công!" : "❌ Thất bại: " + message));
                } catch (Exception e) {
                    System.err.println("[SnipeGuardLive] Lỗi parse BID_RESULT: " + e.getMessage());
                }
            }

            // ════════════════════════════════════════════════════════════
            //  ✨ AUCTION_EXTENDED — TÍNH NĂNG MỚI (SnipeGuard)
            //  Server gửi khi phiên được gia hạn vì có bid trong 60s cuối
            // ════════════════════════════════════════════════════════════
            case "AUCTION_EXTENDED" -> handleAuctionExtended(msg.getPayload());

            // ── Phiên kết thúc ───────────────────────────────────────────
            case "AUCTION_ENDED" -> {
                Platform.runLater(() -> {
                    stopCountdown();
                    markAuctionEnded();

                    try {
                        JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                        String winnerName = dto.has("winnerName") && !dto.get("winnerName").isJsonNull()
                                ? dto.get("winnerName").getAsString() : null;
                        double finalPrice = dto.has("finalPrice")
                                ? dto.get("finalPrice").getAsDouble() : 0;
                        String message    = dto.has("message") && !dto.get("message").isJsonNull()
                                ? dto.get("message").getAsString() : null;

                        if (message != null) {
                            addLog(message);
                        } else if (winnerName != null && finalPrice > 0) {
                            addLog(String.format("🏆 Người thắng: %s — Giá: %,.0f VNĐ",
                                    winnerName, finalPrice));
                        } else {
                            addLog("Phiên kết thúc — không có người đặt giá.");
                        }
                    } catch (Exception e) {
                        addLog("🏁 Phiên đấu giá đã kết thúc!");
                    }
                });
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  handleAuctionExtended — xử lý SnipeGuard gia hạn
    // ─────────────────────────────────────────────────────────────────────
    private void handleAuctionExtended(String payload) {
        try {
            JsonObject dto = gson.fromJson(payload, JsonObject.class);

            String newEndTimeStr   = dto.has("newEndTime")    ? dto.get("newEndTime").getAsString()    : null;
            int    extendedBy      = dto.has("extendedBy")    ? dto.get("extendedBy").getAsInt()       : 60;
            long   wasSecondsLeft  = dto.has("wasSecondsLeft")? dto.get("wasSecondsLeft").getAsLong()  : 0;
            String message         = dto.has("message")       ? dto.get("message").getAsString()       : null;

            if (newEndTimeStr == null || currentItem == null) return;

            LocalDateTime newEnd = LocalDateTime.parse(newEndTimeStr.replace(" ", "T"));
            long newSeconds = java.time.Duration
                    .between(LocalDateTime.now(), newEnd)
                    .getSeconds();
            if (newSeconds <= 0) return;

            Platform.runLater(() -> {
                // 1. Cập nhật item
                currentItem.setEndTime(newEnd);

                // 2. Cập nhật bộ đếm ngược
                secondsRemaining = newSeconds;

                // 3. Restart countdown timer
                stopCountdown();
                startCountdownTimer();

                // 4. Flash màu cam báo hiệu gia hạn
                flashExtendedAnimation(extendedBy);

                // 5. Log thông báo
                String logMsg = message != null ? message
                        : String.format("⏰ SnipeGuard: phiên gia hạn +%ds (bid lúc còn %ds)",
                                extendedBy, wasSecondsLeft);
                addLog(logMsg);

                System.out.printf("[SnipeGuardLive] AUCTION_EXTENDED nhận: newEnd=%s, +%ds%n",
                        newEndTimeStr, extendedBy);
            });

        } catch (Exception e) {
            System.err.println("[SnipeGuardLive] Lỗi parse AUCTION_EXTENDED: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  flashExtendedAnimation — hiệu ứng đổi màu khi gia hạn
    // ─────────────────────────────────────────────────────────────────────
    private void flashExtendedAnimation(int extendedBySeconds) {
        if (lblCountdown == null || isFlashing) return;
        isFlashing = true;

        // Đổi sang màu cam (báo hiệu gia hạn)
        lblCountdown.setStyle(COLOR_EXTENDED);

        // Sau 2 giây trở về màu bình thường theo thời gian còn lại
        Timeline flash = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> {
                    isFlashing = false;
                    updateCountdownStyle(); // trả về màu đúng theo secondsRemaining
                })
        );
        flash.setCycleCount(1);
        flash.play();

        // Thêm text tạm thời hiển thị số giây gia hạn
        String originalText = lblCountdown.getText();
        lblCountdown.setText("+" + extendedBySeconds + "s !");
        Timeline restoreText = new Timeline(
                new KeyFrame(Duration.seconds(1.5), e ->
                        Platform.runLater(this::updateCountdownLabel))
        );
        restoreText.setCycleCount(1);
        restoreText.play();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Countdown logic
    // ─────────────────────────────────────────────────────────────────────
    private void startCountdownFromItem() {
        secondsRemaining = currentItem.getSecondsRemaining();
        if (secondsRemaining <= 0) {
            markAuctionEnded();
            return;
        }
        startCountdownTimer();
    }

    private void startCountdownTimer() {
        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    secondsRemaining--;
                    updateCountdownLabel();
                    updateCountdownStyle();
                    if (secondsRemaining <= 0) {
                        stopCountdown();
                        markAuctionEnded();
                    }
                })
        );
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.playFromStart();
        updateCountdownLabel();
        updateCountdownStyle();
    }

    private void stopCountdown() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
            countdownTimeline = null;
        }
    }

    private void updateCountdownLabel() {
        if (lblCountdown == null || isFlashing) return;
        long h = secondsRemaining / 3600;
        long m = (secondsRemaining % 3600) / 60;
        long s = secondsRemaining % 60;
        lblCountdown.setText(h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s));
    }

    /**
     * Cập nhật màu label theo mức độ thời gian còn lại:
     *  > 300s (5 phút)  : xanh lá  (bình thường)
     *  60~300s          : vàng     (cảnh báo — cũng là cửa sổ SnipeGuard)
     *  < 60s            : đỏ       (nguy hiểm)
     */
    private void updateCountdownStyle() {
        if (lblCountdown == null || isFlashing) return;
        if (secondsRemaining > 300) {
            lblCountdown.setStyle(COLOR_NORMAL);
        } else if (secondsRemaining > 60) {
            lblCountdown.setStyle(COLOR_WARNING);
        } else {
            lblCountdown.setStyle(COLOR_DANGER);
        }
    }

    private void markAuctionEnded() {
        if (lblCountdown != null) {
            lblCountdown.setText("KẾT THÚC!");
            lblCountdown.setStyle(COLOR_ENDED);
        }
        if (txtBidAmount != null) txtBidAmount.setDisable(true);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Place Bid
    // ─────────────────────────────────────────────────────────────────────
    @FXML
    public void onPlaceBidClick(ActionEvent event) {
        if (currentItem == null) return;
        if (secondsRemaining <= 0) { addLog("❌ Phiên đấu giá đã kết thúc."); return; }

        String input = txtBidAmount.getText().trim();
        if (input.isEmpty()) return;

        double newAmount;
        try {
            newAmount = Double.parseDouble(input.replaceAll(",", ""));
        } catch (NumberFormatException e) {
            addLog("❌ Vui lòng nhập số tiền hợp lệ!"); return;
        }

        double minBid = currentItem.getCurrentBid() + currentItem.getBidIncrement();
        if (newAmount < minBid) {
            addLog(String.format("❌ Giá phải ít nhất %,.0f VNĐ (bước giá +%,.0f VNĐ)",
                    minBid, currentItem.getBidIncrement()));
            return;
        }

        // Cảnh báo nếu đang trong cửa sổ SnipeGuard
        if (secondsRemaining <= 60) {
            addLog(String.format("⚠️  Còn %ds — SnipeGuard sẽ gia hạn nếu bid thành công!", secondsRemaining));
        }

        client.send(new Message("PLACE_BID", gson.toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId",  UserSession.getInstance().getUserId(),
                "amount",    newAmount
        ))));
        txtBidAmount.clear();
        addLog(String.format("📤 Đã gửi mức giá: %,.0f VNĐ — Đang chờ xác nhận...", newAmount));
    }

    @FXML
    public void onLeaveRoomClick(ActionEvent event) {
        stopCountdown();
        client.removeListener(listener);
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiết sản phẩm");
    }

    // ─────────────────────────────────────────────────────────────────────
    //  UI helpers
    // ─────────────────────────────────────────────────────────────────────
    private void populateProductInfo() {
        if (lblProductName != null) lblProductName.setText(currentItem.getName());
        updateCurrentBidLabel(currentItem.getCurrentBid());
    }

    private void updateCurrentBidLabel(double bid) {
        if (lblCurrentBid != null)
            lblCurrentBid.setText(String.format("Current Bid: %,.0f VNĐ", bid));
    }

    private void setupTable() {
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colUser.setCellValueFactory(c -> c.getValue().userProperty());
        colAmount.setCellValueFactory(c -> c.getValue().amountProperty());
        tableBidHistory.setItems(bidHistory);
        if (listNotifications != null) listNotifications.setItems(notifications);
    }

    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu cao nhất");
        if (priceChart != null) {
            priceChart.getData().clear();
            priceChart.getData().add(priceSeries);
            priceChart.setAnimated(false);
            priceChart.setTitle("Diễn biến giá theo thời gian thực");
            NumberAxis yAxis = (NumberAxis) priceChart.getYAxis();
            yAxis.setForceZeroInRange(false);
            yAxis.setAutoRanging(true);
        }
    }

    private void addChartPoint(double price) {
        if (priceChart == null || priceSeries == null) return;
        String time = LocalTime.now().format(timeFmt);
        XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(time, price);
        priceSeries.getData().add(dataPoint);

        Platform.runLater(() -> {
            if (dataPoint.getNode() != null) {
                Tooltip tip = new Tooltip(
                        String.format("⏱ %s%n💰 %,.0f VNĐ", time, price));
                tip.setStyle("-fx-background-color:#1a1f2e; -fx-text-fill:white;" +
                        "-fx-font-size:12px; -fx-padding:6 10;");
                Tooltip.install(dataPoint.getNode(), tip);
            }
        });

        if (priceSeries.getData().size() > 20)
            priceSeries.getData().remove(0);
    }

    private void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        Platform.runLater(() -> notifications.add(0, "[" + time + "] " + message));
    }
}
