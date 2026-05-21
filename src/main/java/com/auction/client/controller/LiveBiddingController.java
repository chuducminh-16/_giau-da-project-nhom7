package com.auction.client.controller;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;
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
import javafx.scene.control.*;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

public class LiveBiddingController implements Initializable {

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

    private Item     currentItem;
    private XYChart.Series<String, Number> priceSeries;
    private Timeline countdownTimeline;
    private long     secondsRemaining;

    private final ObservableList<BidItem> bidHistory    = FXCollections.observableArrayList();
    private final ObservableList<String>  notifications = FXCollections.observableArrayList();

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupChart();
        client.addListener(listener);

        currentItem = SelectedProductSession.getInstance().getProduct();

        if (currentItem != null) {
            populateProductInfo();
            startCountdownFromItem();
            client.send(new Message("WATCH_AUCTION",
                    gson.toJson(Map.of("auctionId", currentItem.getId()))));
            addChartPoint(currentItem.getCurrentBid());
            addLog("Chào mừng vào phòng đấu giá: " + currentItem.getName());
        } else {
            addLog("Không tìm thấy thông tin sản phẩm!");
        }
    }

    private void populateProductInfo() {
        if (lblProductName != null) lblProductName.setText(currentItem.getName());
        updateCurrentBidLabel(currentItem.getCurrentBid());
    }

    private void updateCurrentBidLabel(double bid) {
        if (lblCurrentBid != null)
            lblCurrentBid.setText(String.format("Current Bid: %,.0f VNĐ", bid));
    }

    private void startCountdownFromItem() {
        secondsRemaining = currentItem.getSecondsRemaining();
        if (secondsRemaining <= 0) {
            lblCountdown.setText("ĐÃ KẾT THÚC");
            lblCountdown.setStyle("-fx-text-fill: #a0aec0;");
            txtBidAmount.setDisable(true);
            return;
        }
        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    secondsRemaining--;
                    updateCountdownLabel();
                    if (secondsRemaining <= 0) {
                        countdownTimeline.stop();
                        onAuctionEnded();
                    }
                })
        );
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.playFromStart();
        updateCountdownLabel();
    }

    private void updateCountdownLabel() {
        long h = secondsRemaining / 3600;
        long m = (secondsRemaining % 3600) / 60;
        long s = secondsRemaining % 60;
        lblCountdown.setText(h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s));
        if (secondsRemaining <= 60)
            lblCountdown.setStyle("-fx-text-fill:#fc8181; -fx-font-size:22; -fx-font-weight:bold;");
    }

    private void onAuctionEnded() {
        lblCountdown.setText("KẾT THÚC!");
        lblCountdown.setStyle("-fx-text-fill:#a0aec0;");
        txtBidAmount.setDisable(true);
        addLog("Phiên đấu giá đã kết thúc!");
    }

    @FXML public void onPlaceBidClick(ActionEvent event) {
        if (currentItem == null) return;
        if (secondsRemaining <= 0) { addLog("Phiên đấu giá đã kết thúc."); return; }

        String input = txtBidAmount.getText().trim();
        if (input.isEmpty()) return;

        double newAmount;
        try {
            newAmount = Double.parseDouble(input.replaceAll(",", ""));
        } catch (NumberFormatException e) {
            addLog("Lỗi: Vui lòng nhập số tiền hợp lệ!"); return;
        }

        double minBid = currentItem.getCurrentBid() + currentItem.getBidIncrement();
        if (newAmount < minBid) {
            addLog(String.format("Giá phải ít nhất %,.0f VNĐ (tăng thêm %,.0f VNĐ)",
                    minBid, currentItem.getBidIncrement()));
            return;
        }

        String payload = gson.toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId",  UserSession.getInstance().getUserId(),
                "amount",    newAmount
        ));
        client.send(new Message("PLACE_BID", payload));
        txtBidAmount.clear();
        addLog(String.format("Đã gửi mức giá: %,.0f VNĐ — Đang chờ xác nhận...", newAmount));
    }

    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            case "BID_UPDATE" -> {
                BidUpdateDto dto = gson.fromJson(msg.getPayload(), BidUpdateDto.class);
                if (!dto.productId.equals(currentItem.getId())) return;
                Platform.runLater(() -> {
                    currentItem.setCurrentBid(dto.newBid);
                    updateCurrentBidLabel(dto.newBid);
                    String time = LocalTime.now().format(timeFmt);
                    bidHistory.add(0, new BidItem(time, dto.bidderName,
                            String.format("%,.0f VNĐ", dto.newBid)));
                    addChartPoint(dto.newBid);
                    addLog(String.format("%s vừa đặt %,.0f VNĐ", dto.bidderName, dto.newBid));
                });
            }

            case "BID_RESULT" -> {
                BidResultDto dto = gson.fromJson(msg.getPayload(), BidResultDto.class);
                Platform.runLater(() ->
                        addLog(dto.success ? "✅ Đặt giá thành công!" : "❌ Thất bại: " + dto.message));
            }

            case "AUCTION_ENDED" -> Platform.runLater(() -> {
                if (countdownTimeline != null) countdownTimeline.stop();
                onAuctionEnded();
            });
        }
    }

    private void setupTable() {
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colUser.setCellValueFactory(c -> c.getValue().userProperty());
        colAmount.setCellValueFactory(c -> c.getValue().amountProperty());
        tableBidHistory.setItems(bidHistory);
        listNotifications.setItems(notifications);
    }

    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu cao nhất");
        priceChart.getData().add(priceSeries);
        priceChart.setAnimated(false);
        priceChart.setTitle("Diễn biến giá theo thời gian thực");
        priceChart.getXAxis().setLabel("Thời gian");
        priceChart.getYAxis().setLabel("Giá (VNĐ)");
        NumberAxis yAxis = (NumberAxis) priceChart.getYAxis();
        yAxis.setForceZeroInRange(false);
        yAxis.setAutoRanging(true);
        priceChart.setCreateSymbols(true);
    }

    private void addChartPoint(double price) {
        String time = LocalTime.now().format(timeFmt);
        XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(time, price);
        priceSeries.getData().add(dataPoint);
        if (dataPoint.getNode() != null) {
            Tooltip tooltip = new Tooltip(String.format("⏱ %s\n💰 %,.0f VNĐ", time, price));
            tooltip.setStyle("-fx-background-color:#1a1f2e; -fx-text-fill:white;" +
                    "-fx-font-size:12px; -fx-padding:6 10;");
            Tooltip.install(dataPoint.getNode(), tooltip);
        }
        if (priceSeries.getData().size() > 20)
            priceSeries.getData().remove(0);
    }

    @FXML public void onLeaveRoomClick(ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        client.removeListener(listener);
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiết sản phẩm");
    }

    private void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        notifications.add(0, "[" + time + "] " + message);
    }

    private record BidUpdateDto(String productId, double newBid, String bidderName) {}
    private record BidResultDto(boolean success, String message) {}
}