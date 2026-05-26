package com.auction.client.controller;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
import com.auction.client.handler.snipeguard.SnipeGuardMessageHandler; // 👈 ĐÃ ĐỔI TÊN ĐƯỜNG IMPORT MỚI
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Item;

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
 * 🎮 LỚP ĐIỀU KHIỂN PHÒNG ĐẤU GIÁ REALTIME (SNIPE GUARD LIVE BIDDING UI CONTROLLER)
 * - Nhiệm vụ: Chỉ quản lý trạng thái hiển thị của các nút bấm, biểu đồ giá, bảng lịch sử và đồng hồ Timeline.
 */
public class SnipeGuardLiveBiddingController implements Initializable {

    // ── FXML bindings ────────────────────────────────────────────────────
    @FXML private Label     lblCountdown;
    @FXML private Label     lblProductName;
    @FXML private Label     lblCurrentBid;
    @FXML private TextField txtBidAmount;
    @FXML private LineChart<String, Number> priceChart;

    @FXML private TableView<BidItem>          tableBidHistory;
    @FXML private TableColumn<BidItem,String> colTime;
    @FXML private TableColumn<BidItem,String> colUser;
    @FXML private TableColumn<BidItem,String> colAmount;
    @FXML private ListView<String>            listNotifications;

    // ── Giao diện & Trạng thái ───────────────────────────────────────────
    private Item currentItem;
    private String itemId;
    private XYChart.Series<String, Number> priceSeries;
    
    private Timeline countdownTimeline;
    private long secondsRemaining;
    private boolean isFlashing = false;

    // Các chuỗi định dạng CSS Style cho nhãn đếm ngược theo mức độ khẩn cấp
    private static final String COLOR_NORMAL   = "-fx-text-fill:#68d391; -fx-font-size:22; -fx-font-weight:bold;";
    private static final String COLOR_WARNING  = "-fx-text-fill:#d69e2e; -fx-font-size:22; -fx-font-weight:bold;";
    private static final String COLOR_DANGER   = "-fx-text-fill:#fc8181; -fx-font-size:22; -fx-font-weight:bold;";
    private static final String COLOR_EXTENDED = "-fx-text-fill:#f6ad55; -fx-font-size:24; -fx-font-weight:bold;";
    private static final String COLOR_ENDED    = "-fx-text-fill:#a0aec0; -fx-font-size:18; -fx-font-weight:bold;";

    private final ObservableList<BidItem> bidHistory   = FXCollections.observableArrayList();
    private final ObservableList<String>  notifications = FXCollections.observableArrayList();

    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Các thành phần kết nối Tầng logic ────────────────────────────────
    private SnipeGuardMessageHandler messageHandler; // 👈 ĐỒI KIỂU DỮ LIỆU SANG CLASS MỚI
    private NetworkClient.MessageListener listener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Cấu hình bảng hiển thị và biểu đồ tọa độ tuyến tính
        setupTable();
        setupChart();

        // 2. Liên kết kiến trúc bộ điều phối tin nhắn độc lập
        this.messageHandler = new SnipeGuardMessageHandler(this); // 👈 KHỞI TẠO CLASS MỚI
        this.listener = msg -> messageHandler.handleServerMessage(msg);
        client.addListener(listener);

        // 3. Đọc mã ID sản phẩm từ phiên lưu trữ toàn cục
        itemId = SelectedProductSession.getInstance().getProductId();

        if (itemId != null) {
            addLog("🔌 Đang kết nối phòng đấu giá...");
            client.send(new Message("GET_PRODUCT_DETAIL",
                    messageHandler.getGson().toJson(Map.of("itemId", itemId))));
        } else {
            addLog("❌ Không tìm thấy thông tin sản phẩm!");
        }
    }

    /**
     * Sự kiện nút bấm: Gửi mức giá muốn đấu lên Server hệ thống.
     */
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
            addLog("❌ Vui lòng nhập số tiền hợp lệ!"); 
            return;
        }

        double minBid = currentItem.getCurrentBid() + currentItem.getBidIncrement();
        if (newAmount < minBid) {
            addLog(String.format("❌ Giá phải ít nhất %,.0f VNĐ (bước giá +%,.0f VNĐ)",
                    minBid, currentItem.getBidIncrement()));
            return;
        }

        // Đưa ra cảnh báo hệ thống SnipeGuard sắp can thiệp kéo dài thời gian phiên
        if (secondsRemaining <= 60) {
            addLog(String.format("⚠️ Còn %ds — SnipeGuard sẽ gia hạn nếu bid thành công!", secondsRemaining));
        }

        client.send(new Message("PLACE_BID", messageHandler.getGson().toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId",  UserSession.getInstance().getUserId(),
                "amount",    newAmount
        ))));
        txtBidAmount.clear();
        addLog(String.format("📤 Đã gửi mức giá: %,.0f VNĐ — Đang chờ xác nhận...", newAmount));
    }

    /**
     * Sự kiện nút bấm: Rời khỏi phòng đấu giá Realtime, ngắt cổng nghe để tránh rò rỉ bộ nhớ.
     */
    @FXML
    public void onLeaveRoomClick(ActionEvent event) {
        stopCountdown();
        client.removeListener(listener);
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiết sản phẩm");
    }

    // ─── ⏳ QUẢN LÝ TIMELINE ĐẾM NGƯỢC THỜI GIAN REALTIME ─────────────────────

    public void startCountdownFromItem() {
        secondsRemaining = currentItem.getSecondsRemaining();
        if (secondsRemaining <= 0) {
            markAuctionEnded();
            return;
        }
        startCountdownTimer();
    }

    public void startCountdownTimer() {
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

    public void stopCountdown() {
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

    public void flashExtendedAnimation(int extendedBySeconds) {
        if (lblCountdown == null || isFlashing) return;
        isFlashing = true;
        lblCountdown.setStyle(COLOR_EXTENDED);

        Timeline flash = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> {
                    isFlashing = false;
                    updateCountdownStyle();
                })
        );
        flash.setCycleCount(1);
        flash.play();

        lblCountdown.setText("+" + extendedBySeconds + "s !");
        Timeline restoreText = new Timeline(
                new KeyFrame(Duration.seconds(1.5), e ->
                        Platform.runLater(this::updateCountdownLabel))
        );
        restoreText.setCycleCount(1);
        restoreText.play();
    }

    public void markAuctionEnded() {
        if (lblCountdown != null) {
            lblCountdown.setText("KẾT THÚC!");
            lblCountdown.setStyle(COLOR_ENDED);
        }
        if (txtBidAmount != null) txtBidAmount.setDisable(true);
    }

    // ─── 📊 ĐỒ HỌA HỖ TRỢ HIỂN THỊ UI (TABLEVIEW & LINECHART) ─────────────────

    public void populateProductInfo() {
        if (lblProductName != null) lblProductName.setText(currentItem.getName());
        updateCurrentBidLabel(currentItem.getCurrentBid());
    }

    public void updateCurrentBidLabel(double bid) {
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

    public void addChartPoint(double price) {
        if (priceChart == null || priceSeries == null) return;
        String time = LocalTime.now().format(timeFmt);
        XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(time, price);
        priceSeries.getData().add(dataPoint);

        Platform.runLater(() -> {
            if (dataPoint.getNode() != null) {
                Tooltip tip = new Tooltip(String.format("⏱ %s%n💰 %,.0f VNĐ", time, price));
                tip.setStyle("-fx-background-color:#1a1f2e; -fx-text-fill:white; -fx-font-size:12px; -fx-padding:6 10;");
                Tooltip.install(dataPoint.getNode(), tip);
            }
        });

        if (priceSeries.getData().size() > 20)
            priceSeries.getData().remove(0);
    }

    public void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        Platform.runLater(() -> notifications.add(0, "[" + time + "] " + message));
    }

    // ─── ⚙️ ACCESSORS (GETTERS / SETTERS) ───────────────────────────────────
    public Item getCurrentItem() { return currentItem; }
    public void setCurrentItem(Item currentItem) { this.currentItem = currentItem; }
    public ObservableList<BidItem> getBidHistory() { return bidHistory; }
    public void setSecondsRemaining(long secondsRemaining) { this.secondsRemaining = secondsRemaining; }
}