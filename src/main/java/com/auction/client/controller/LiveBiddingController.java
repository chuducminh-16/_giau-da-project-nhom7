package com.auction.client.controller;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
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

import java.lang.reflect.Type;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * LiveBiddingController
 *
 * FIX so voi ban goc:
 *   1. Them xu ly "TIME_EXTENDED" — reset dong ho dem nguoc khi anti-sniping gia han
 *   2. Them xu ly "BID_HISTORY_RESPONSE" — hien thi lich su bid khi vao phong
 *   3. Gui GET_BID_HISTORY ngay khi initialize() de load lich su co san
 *   4. Them nut dang ky / huy Auto-Bid
 */
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

    // Auto-bid controls (co the null neu FXML chua them)
    @FXML private TextField txtMaxBid;
    @FXML private TextField txtIncrement;

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

            // Dang ky xem phien nay (de nhan broadcast BID_UPDATE, TIME_EXTENDED)
            client.send(new Message("WATCH_AUCTION",
                    gson.toJson(Map.of("auctionId", currentItem.getId()))));

            // FIX: Load lich su bid co san khi vao phong
            client.send(new Message("GET_BID_HISTORY",
                    gson.toJson(Map.of("productId", currentItem.getId()))));

            addChartPoint(currentItem.getCurrentBid());
            addLog("Chao mung vao phong dau gia: " + currentItem.getName());
        } else {
            addLog("Khong tim thay thong tin san pham!");
        }
    }

    private void populateProductInfo() {
        if (lblProductName != null) lblProductName.setText(currentItem.getName());
        updateCurrentBidLabel(currentItem.getCurrentBid());
    }

    private void updateCurrentBidLabel(double bid) {
        if (lblCurrentBid != null)
            lblCurrentBid.setText(String.format("Current Bid: %,.0f VND", bid));
    }

    private void startCountdownFromItem() {
        secondsRemaining = currentItem.getSecondsRemaining();
        if (secondsRemaining <= 0) {
            if (lblCountdown != null) {
                lblCountdown.setText("DA KET THUC");
                lblCountdown.setStyle("-fx-text-fill: #a0aec0;");
            }
            if (txtBidAmount != null) txtBidAmount.setDisable(true);
            return;
        }
        startCountdownTimer();
    }

    private void startCountdownTimer() {
        if (countdownTimeline != null) countdownTimeline.stop();
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
        if (lblCountdown == null) return;
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
        if (lblCountdown != null) {
            lblCountdown.setText("KET THUC!");
            lblCountdown.setStyle("-fx-text-fill:#a0aec0;");
        }
        if (txtBidAmount != null) txtBidAmount.setDisable(true);
        addLog("Phien dau gia da ket thuc!");
    }

    // ── Dat gia ──────────────────────────────────────────────────────────
    @FXML
    public void onPlaceBidClick(ActionEvent event) {
        if (currentItem == null) return;
        if (secondsRemaining <= 0) { addLog("Phien dau gia da ket thuc."); return; }

        String input = txtBidAmount.getText().trim();
        if (input.isEmpty()) return;

        double newAmount;
        try {
            newAmount = Double.parseDouble(input.replaceAll(",", ""));
        } catch (NumberFormatException e) {
            addLog("Loi: Vui long nhap so tien hop le!"); return;
        }

        double minBid = currentItem.getCurrentBid() + currentItem.getBidIncrement();
        if (newAmount < minBid) {
            addLog(String.format("Gia phai it nhat %,.0f VND (tang them %,.0f VND)",
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
        addLog(String.format("Da gui muc gia: %,.0f VND - Dang cho xac nhan...", newAmount));
    }

    // ── Dang ky Auto-Bid ─────────────────────────────────────────────────
    @FXML
    public void onRegisterAutoBidClick(ActionEvent event) {
        if (currentItem == null) return;
        if (txtMaxBid == null || txtIncrement == null) return;

        try {
            double maxBid     = Double.parseDouble(txtMaxBid.getText().trim().replaceAll(",", ""));
            double increment  = Double.parseDouble(txtIncrement.getText().trim().replaceAll(",", ""));

            String payload = gson.toJson(Map.of(
                    "itemId",    currentItem.getId(),
                    "maxBid",    maxBid,
                    "increment", increment
            ));
            client.send(new Message("REGISTER_AUTO_BID", payload));
            addLog(String.format("Dang ky auto-bid: max=%.0f, buoc=%.0f", maxBid, increment));
        } catch (NumberFormatException e) {
            addLog("Loi: maxBid va increment phai la so hop le.");
        }
    }

    // ── Huy Auto-Bid ─────────────────────────────────────────────────────
    @FXML
    public void onCancelAutoBidClick(ActionEvent event) {
        if (currentItem == null) return;
        String payload = gson.toJson(Map.of("itemId", currentItem.getId()));
        client.send(new Message("CANCEL_AUTO_BID", payload));
        addLog("Da gui yeu cau huy auto-bid.");
    }

    // ── Xu ly message tu server ───────────────────────────────────────────
    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            case "BID_UPDATE" -> {
                BidUpdateDto dto = gson.fromJson(msg.getPayload(), BidUpdateDto.class);
                if (currentItem == null || !dto.productId.equals(currentItem.getId())) return;
                Platform.runLater(() -> {
                    currentItem.setCurrentBid(dto.newBid);
                    updateCurrentBidLabel(dto.newBid);
                    String time = LocalTime.now().format(timeFmt);
                    bidHistory.add(0, new BidItem(time, dto.bidderName,
                            String.format("%,.0f VND", dto.newBid)));
                    addChartPoint(dto.newBid);
                    addLog(String.format("%s vua dat %,.0f VND", dto.bidderName, dto.newBid));
                });
            }

            case "BID_RESULT" -> {
                BidResultDto dto = gson.fromJson(msg.getPayload(), BidResultDto.class);
                Platform.runLater(() ->
                        addLog(dto.success ? "Dat gia thanh cong!" : "That bai: " + dto.message));
            }

            // FIX: Xu ly TIME_EXTENDED — reset dong ho dem nguoc khi anti-sniping gia han
            case "TIME_EXTENDED" -> {
                TimeExtendedDto dto = gson.fromJson(msg.getPayload(), TimeExtendedDto.class);
                if (currentItem == null || !dto.productId.equals(currentItem.getId())) return;
                Platform.runLater(() -> {
                    try {
                        LocalDateTime newEnd = LocalDateTime.parse(
                                dto.newEndTime.replace(" ", "T"));
                        secondsRemaining = java.time.Duration.between(
                                LocalDateTime.now(), newEnd).getSeconds();
                        if (secondsRemaining > 0) {
                            // Khoi dong lai countdown voi thoi gian moi
                            startCountdownTimer();
                            addLog("Phien duoc gia han! Het gio luc: " + dto.newEndTime);
                        }
                    } catch (Exception e) {
                        System.err.println("[LiveBidding] Loi parse TIME_EXTENDED: " + e.getMessage());
                    }
                });
            }

            // FIX: Xu ly BID_HISTORY_RESPONSE — hien thi lich su khi vao phong
            case "BID_HISTORY_RESPONSE" -> {
                Platform.runLater(() -> {
                    try {
                        Type listType = new TypeToken<List<BidRecordDto>>(){}.getType();
                        List<BidRecordDto> records = gson.fromJson(msg.getPayload(), listType);
                        if (records == null) return;
                        bidHistory.clear();
                        for (BidRecordDto r : records) {
                            bidHistory.add(new BidItem(
                                    r.createdAt != null ? r.createdAt : "-",
                                    r.username  != null ? r.username  : r.bidderId,
                                    String.format("%,.0f VND", r.amount)
                            ));
                        }
                        if (!records.isEmpty())
                            addLog("Da tai " + records.size() + " luot dat gia truoc do.");
                    } catch (Exception e) {
                        System.err.println("[LiveBidding] Loi parse BID_HISTORY: " + e.getMessage());
                    }
                });
            }

            case "AUTO_BID_RESULT" -> {
                AutoBidResultDto dto = gson.fromJson(msg.getPayload(), AutoBidResultDto.class);
                Platform.runLater(() ->
                        addLog(dto.success ? "Auto-bid: " + dto.message : "Auto-bid loi: " + dto.message));
            }

            case "AUCTION_ENDED" -> Platform.runLater(() -> {
                if (countdownTimeline != null) countdownTimeline.stop();
                onAuctionEnded();
                try {
                    AuctionEndedDto dto = gson.fromJson(msg.getPayload(), AuctionEndedDto.class);
                    if (dto.message != null) addLog(dto.message);
                } catch (Exception ignored) {}
            });
        }
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
        priceSeries.setName("Gia dau cao nhat");
        if (priceChart != null) {
            priceChart.getData().add(priceSeries);
            priceChart.setAnimated(false);
            NumberAxis yAxis = (NumberAxis) priceChart.getYAxis();
            yAxis.setForceZeroInRange(false);
            yAxis.setAutoRanging(true);
        }
    }

    private void addChartPoint(double price) {
        String time = LocalTime.now().format(timeFmt);
        priceSeries.getData().add(new XYChart.Data<>(time, price));
        if (priceSeries.getData().size() > 20)
            priceSeries.getData().remove(0);
    }

    @FXML
    public void onLeaveRoomClick(ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        client.removeListener(listener);
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiet san pham");
    }

    private void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        Platform.runLater(() -> notifications.add(0, "[" + time + "] " + message));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────
    private record BidUpdateDto(String productId, double newBid, String bidderName) {}
    private record BidResultDto(boolean success, String message) {}
    private record TimeExtendedDto(String productId, String newEndTime) {}
    private record AutoBidResultDto(boolean success, String message) {}
    private record AuctionEndedDto(long auctionId, String itemId,
                                   String winnerId, String winnerName,
                                   double finalPrice, String message) {}
    private record BidRecordDto(String bidderId, String username,
                                double amount, String createdAt) {}
}
