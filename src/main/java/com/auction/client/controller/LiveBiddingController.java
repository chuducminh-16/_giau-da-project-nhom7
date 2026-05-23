package com.auction.client.controller;

import java.net.URL;
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
    private String   itemId;
    private XYChart.Series<String, Number> priceSeries;
    private Timeline countdownTimeline;
    private long     secondsRemaining;

    private final ObservableList<BidItem> bidHistory    = FXCollections.observableArrayList();
    private final ObservableList<String>  notifications = FXCollections.observableArrayList();

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    // ── Custom Gson để deserialize abstract Item ──────────────────────────
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupChart();
        client.addListener(listener);

        // Lấy itemId từ Session, fetch fresh data từ server
        itemId = SelectedProductSession.getInstance().getProductId();

        if (itemId != null) {
            addLog("Đang kết nối phòng đấu giá...");
            client.send(new Message("GET_PRODUCT_DETAIL",
                    gson.toJson(Map.of("itemId", itemId))));
        } else {
            addLog("Không tìm thấy thông tin sản phẩm!");
        }
    }

    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            // Nhận fresh data khi vừa vào phòng
            case "PRODUCT_DETAIL_RESPONSE" -> {
                try {
                    JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                    if (!root.has("success") || !root.get("success").getAsBoolean()) {
                        Platform.runLater(() -> addLog("Không tìm thấy sản phẩm!"));
                        return;
                    }
                    Item item = gson.fromJson(root.get("item"), Item.class);
                    if (item == null) return;

                    Platform.runLater(() -> {
                        currentItem = item;
                        populateProductInfo();
                        startCountdownFromItem();
                        // Đăng ký nhận BID_UPDATE realtime
                        client.send(new Message("WATCH_AUCTION",
                                gson.toJson(Map.of("auctionId", item.getId()))));
                        addChartPoint(item.getCurrentBid());
                        addLog("Chào mừng vào phòng đấu giá: " + item.getName());
                    });
                } catch (Exception e) {
                    System.err.println("[LiveBidding] Lỗi parse PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
                }
            }

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();
                    if (currentItem == null || !currentItem.getId().equals(productId)) return;

                    double newBid        = dto.get("newBid").getAsDouble();
                    String bidderName    = dto.has("bidderName")
                            ? dto.get("bidderName").getAsString() : "Unknown";

                    Platform.runLater(() -> {
                        currentItem.setCurrentBid(newBid);
                        updateCurrentBidLabel(newBid);
                        String time = LocalTime.now().format(timeFmt);
                        bidHistory.add(0, new BidItem(time, bidderName,
                                String.format("%,.0f VNĐ", newBid)));
                        addChartPoint(newBid);
                        addLog(String.format("%s vừa đặt %,.0f VNĐ", bidderName, newBid));
                    });
                } catch (Exception e) {
                    System.err.println("[LiveBidding] Lỗi parse BID_UPDATE: " + e.getMessage());
                }
            }

            case "BID_RESULT" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    boolean success  = dto.get("success").getAsBoolean();
                    String  message  = dto.has("message") ? dto.get("message").getAsString() : "";
                    Platform.runLater(() ->
                            addLog(success ? "✅ Đặt giá thành công!" : "❌ Thất bại: " + message));
                } catch (Exception e) {
                    System.err.println("[LiveBidding] Lỗi parse BID_RESULT: " + e.getMessage());
                }
            }

            // Anti-sniping: server gia hạn thêm thời gian
            case "AUCTION_EXTENDED" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String newEndTimeStr = dto.has("newEndTime")
                            ? dto.get("newEndTime").getAsString() : null;
                    if (newEndTimeStr != null && currentItem != null) {
                        try {
                            java.time.LocalDateTime newEnd =
                                    java.time.LocalDateTime.parse(newEndTimeStr);
                            Platform.runLater(() -> {
                                currentItem.setEndTime(newEnd);
                                secondsRemaining = currentItem.getSecondsRemaining();
                                addLog("⏰ Phiên được gia hạn thêm 60 giây!");
                            });
                        } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    System.err.println("[LiveBidding] Lỗi parse AUCTION_EXTENDED: " + e.getMessage());
                }
            }

            case "AUCTION_ENDED" -> Platform.runLater(() -> {
                if (countdownTimeline != null) countdownTimeline.stop();
                onAuctionEnded();
            });
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

    @FXML public void onLeaveRoomClick(ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        client.removeListener(listener);
        // Session vẫn giữ itemId → Detail sẽ fetch lại từ server
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiết sản phẩm");
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

    private void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        notifications.add(0, "[" + time + "] " + message);
    }
}