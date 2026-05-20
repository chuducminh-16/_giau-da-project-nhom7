package com.auction.client.controller;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
import com.auction.client.model.Product;
<<<<<<< HEAD
<<<<<<< HEAD
import com.auction.client.network.MessageListener;
=======
import com.auction.client.network.Message;
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a
=======
import com.auction.client.network.Message;
>>>>>>> 22190fa5f4cf91b7b51e964d33cd4d3d25722935
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
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
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

public class LiveBiddingController implements Initializable {

    // ── FXML ─────────────────────────────────────────────
    @FXML private Label     lblCountdown;
    @FXML private Label     lblProductName;   // ← thêm vào FXML
    @FXML private Label     lblCurrentBid;    // ← thêm vào FXML
    @FXML private TextField txtBidAmount;
    @FXML private LineChart<String, Number> priceChart;

    @FXML private TableView<BidItem>         tableBidHistory;
    @FXML private TableColumn<BidItem,String> colTime;
    @FXML private TableColumn<BidItem,String> colUser;
    @FXML private TableColumn<BidItem,String> colAmount;

    @FXML private ListView<String> listNotifications;

    // ── State ─────────────────────────────────────────────
    private Product currentProduct;
    private XYChart.Series<String, Number> priceSeries;
    private Timeline  countdownTimeline;
    private long      secondsRemaining;

    private final ObservableList<BidItem>  bidHistory     =
            FXCollections.observableArrayList();
    private final ObservableList<String>   notifications  =
            FXCollections.observableArrayList();

    // ── Tools ─────────────────────────────────────────────
    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkClient.MessageListener listener =
            this::handleServerMessage;

    // ── initialize ────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupChart();

        client.addListener(listener);

        // Lấy sản phẩm từ Singleton
        currentProduct = SelectedProductSession.getInstance().getProduct();

        if (currentProduct != null) {
            populateProductInfo();
            startCountdownFromProduct();

            // Đăng ký nhận BID_UPDATE cho phiên này
<<<<<<< HEAD
<<<<<<< HEAD
            client.send(new MessageListener("WATCH_AUCTION",
=======
            client.send(new Message("WATCH_AUCTION",
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a
=======
            client.send(new Message("WATCH_AUCTION",
>>>>>>> 22190fa5f4cf91b7b51e964d33cd4d3d25722935
                    gson.toJson(Map.of("auctionId",
                            currentProduct.getId()))));

            // Thêm điểm đầu vào chart = giá hiện tại
            addChartPoint(currentProduct.getCurrentBid());
            addLog("Chào mừng vào phòng đấu giá: "
                    + currentProduct.getName());
        } else {
            addLog("Không tìm thấy thông tin sản phẩm!");
        }
    }

    // ── Hiển thị thông tin sản phẩm ──────────────────────
    private void populateProductInfo() {
        if (lblProductName != null)
            lblProductName.setText(currentProduct.getName());
        updateCurrentBidLabel(currentProduct.getCurrentBid());
    }

    private void updateCurrentBidLabel(double bid) {
        if (lblCurrentBid != null)
            lblCurrentBid.setText(
                    String.format("Current Bid: %,.0f VNĐ", bid));
    }

    // ── Đồng hồ đếm ngược dựa trên endTime thật ─────────
    private void startCountdownFromProduct() {
        // Tính số giây còn lại từ endTime thật của sản phẩm
        secondsRemaining = currentProduct.getSecondsRemaining();

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
        updateCountdownLabel(); // hiện ngay lần đầu
    }

    private void updateCountdownLabel() {
        long h = secondsRemaining / 3600;
        long m = (secondsRemaining % 3600) / 60;
        long s = secondsRemaining % 60;

        String text = h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);
        lblCountdown.setText(text);

        // Đổi màu đỏ khi < 1 phút
        if (secondsRemaining <= 60)
            lblCountdown.setStyle(
                    "-fx-text-fill: #fc8181; -fx-font-size: 22;" +
                            "-fx-font-weight: bold;");
    }

    private void onAuctionEnded() {
        lblCountdown.setText("KẾT THÚC!");
        lblCountdown.setStyle("-fx-text-fill: #a0aec0;");
        txtBidAmount.setDisable(true);
        addLog("Phiên đấu giá đã kết thúc!");
    }

    // ── Bấm Place Bid ─────────────────────────────────────
    @FXML
    public void onPlaceBidClick(ActionEvent event) {
        if (currentProduct == null) return;
        if (secondsRemaining <= 0) {
            addLog("Phiên đấu giá đã kết thúc, không thể đặt giá.");
            return;
        }

        String input = txtBidAmount.getText().trim();
        if (input.isEmpty()) return;

        double newAmount;
        try {
            // Cho phép nhập có dấu phẩy: "1,500,000" → "1500000"
            newAmount = Double.parseDouble(input.replaceAll(",", ""));
        } catch (NumberFormatException e) {
            addLog("Lỗi: Vui lòng nhập số tiền hợp lệ!");
            return;
        }

        // Validate: phải cao hơn giá hiện tại ít nhất 1 bước giá
        double minBid = currentProduct.getCurrentBid()
                + currentProduct.getBidIncrement();
        if (newAmount < minBid) {
            addLog(String.format(
                    "Giá phải ít nhất %,.0f VNĐ (tăng thêm %,.0f VNĐ)",
                    minBid, currentProduct.getBidIncrement()));
            return;
        }

        // Gửi lên server
        String payload = gson.toJson(Map.of(
                "productId", currentProduct.getId(),
                "bidderId",  UserSession.getInstance().getUserId(),
                "amount",    newAmount
        ));
<<<<<<< HEAD
<<<<<<< HEAD
        client.send(new MessageListener("PLACE_BID", payload));
=======
        client.send(new Message("PLACE_BID", payload));
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a
=======
        client.send(new Message("PLACE_BID", payload));
>>>>>>> 22190fa5f4cf91b7b51e964d33cd4d3d25722935
        txtBidAmount.clear();
        addLog(String.format("Đã gửi mức giá: %,.0f VNĐ — Đang chờ xác nhận...",
                newAmount));
    }

    // ── Nhận message từ server ────────────────────────────
<<<<<<< HEAD
<<<<<<< HEAD
    private void handleServerMessage(MessageListener msg) {
=======
    private void handleServerMessage(Message msg) {
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a
=======
    private void handleServerMessage(Message msg) {
>>>>>>> 22190fa5f4cf91b7b51e964d33cd4d3d25722935
        switch (msg.getType()) {

            case "BID_UPDATE" -> {
                // Bid mới từ bất kỳ ai (kể cả mình)
                BidUpdateDto dto = gson.fromJson(
                        msg.getPayload(), BidUpdateDto.class);
                if (!dto.productId.equals(currentProduct.getId())) return;

                Platform.runLater(() -> {
                    currentProduct.setCurrentBid(dto.newBid);

                    // Cập nhật label giá
                    updateCurrentBidLabel(dto.newBid);

                    // Thêm vào bảng lịch sử
                    String time = LocalTime.now().format(timeFmt);
                    bidHistory.add(0, new BidItem(
                            time, dto.bidderName,
                            String.format("%,.0f VNĐ", dto.newBid)));

                    // Thêm điểm vào chart
                    addChartPoint(dto.newBid);

                    // Log
                    addLog(String.format("%s vừa đặt %,.0f VNĐ",
                            dto.bidderName, dto.newBid));
                });
            }

            case "BID_RESULT" -> {
                // Kết quả bid của chính mình
                BidResultDto dto = gson.fromJson(
                        msg.getPayload(), BidResultDto.class);
                Platform.runLater(() ->
                        addLog(dto.success
                                ? "✅ Đặt giá thành công!"
                                : "❌ Thất bại: " + dto.message));
            }

            case "AUCTION_ENDED" -> Platform.runLater(() -> {
                if (countdownTimeline != null)
                    countdownTimeline.stop();
                onAuctionEnded();
            });
        }
    }

    // ── Setup bảng lịch sử ───────────────────────────────
    private void setupTable() {
        colTime.setCellValueFactory(
                c -> c.getValue().timeProperty());
        colUser.setCellValueFactory(
                c -> c.getValue().userProperty());
        colAmount.setCellValueFactory(
                c -> c.getValue().amountProperty());
        tableBidHistory.setItems(bidHistory);
        listNotifications.setItems(notifications);
    }

    // ── Setup chart ───────────────────────────────────────
    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Diễn biến giá");
        priceChart.getData().add(priceSeries);
        priceChart.setAnimated(false); // tắt animation để realtime mượt
    }

    private void addChartPoint(double price) {
        String time = LocalTime.now().format(timeFmt);
        priceSeries.getData().add(
                new XYChart.Data<>(time, price));
        // Giữ tối đa 20 điểm — tránh chart quá dày
        if (priceSeries.getData().size() > 20)
            priceSeries.getData().remove(0);
    }

    // ── Leave Room ────────────────────────────────────────
    @FXML
    public void onLeaveRoomClick(ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        client.removeListener(listener);
        SceneEngine.changeScene(event,
                "detail-view.fxml", "Chi tiết sản phẩm");
    }

    // ── Log ───────────────────────────────────────────────
    private void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        notifications.add(0, "[" + time + "] " + message);
    }

    // ── DTOs ──────────────────────────────────────────────
    private record BidUpdateDto(
            String productId, double newBid, String bidderName) {}
    private record BidResultDto(boolean success, String message) {}
}