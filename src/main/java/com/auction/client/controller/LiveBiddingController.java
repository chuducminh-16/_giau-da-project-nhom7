package com.auction.client.controller;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Duration;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

public class LiveBiddingController implements Initializable {

    @FXML private Label lblCountdown;
    @FXML private TextField txtBidAmount;
    @FXML private LineChart<String, Number> priceChart;

    private XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();

    // Bảng lịch sử thả giá
    @FXML private TableView<BidItem> tableBidHistory;
    @FXML private TableColumn<BidItem, String> colTime;
    @FXML private TableColumn<BidItem, String> colUser;
    @FXML private TableColumn<BidItem, String> colAmount;

    // Khung log thông báo
    @FXML private ListView<String> listNotifications;

    private ObservableList<BidItem> bidHistoryData = FXCollections.observableArrayList();
    private ObservableList<String> notificationData = FXCollections.observableArrayList();
    private int timeSeconds = 300; // 5 phút đếm ngược
    private Timeline timeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Cài đặt cột cho bảng
        colTime.setCellValueFactory(cellData -> cellData.getValue().timeProperty());
        colUser.setCellValueFactory(cellData -> cellData.getValue().userProperty());
        colAmount.setCellValueFactory(cellData -> cellData.getValue().amountProperty());

        tableBidHistory.setItems(bidHistoryData);
        listNotifications.setItems(notificationData);

        // Khởi tạo biểu đồ
        priceSeries.setName("Diễn biến giá");
        priceChart.getData().add(priceSeries);

        // Điểm khởi đầu (Giá gốc ban đầu)
        priceSeries.getData().add(new XYChart.Data<>("Bắt đầu", 500000));

        startCountdownTimer();
        addSystemLog("Chào mừng bạn đến phòng đấu giá!");
    }

    private void startCountdownTimer() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            timeSeconds--;
            int minutes = timeSeconds / 60;
            int seconds = timeSeconds % 60;
            lblCountdown.setText(String.format("%02d:%02d", minutes, seconds));

            if (timeSeconds <= 0) {
                timeline.stop();
                lblCountdown.setText("KẾT THÚC!");
                txtBidAmount.setDisable(true); // Khóa không cho nhập giá nữa
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.playFromStart();
    }

    @FXML
    public void onPlaceBidClick(ActionEvent event) {
        String input = txtBidAmount.getText();
        if (input == null || input.isEmpty()) return;

        try {
            double newAmount = Double.parseDouble(input);
            String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            // 1. Cập nhật Bảng (TableView)
            bidHistoryData.add(0, new BidItem(currentTime, "Bạn", input + " VNĐ"));

            // 2. Cập nhật Biểu đồ (Line Chart)
            // Thêm một điểm mới vào đường kẻ
            priceSeries.getData().add(new XYChart.Data<>(currentTime, newAmount));

            // 3. Cập nhật Log (ListView)
            addSystemLog("Bạn vừa đặt mức giá mới: " + input);

            txtBidAmount.clear();

        } catch (NumberFormatException e) {
            addSystemLog("Lỗi: Vui lòng nhập số tiền hợp lệ!");
        }
    }

    public void addSystemLog(String message) {
        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        notificationData.add(0, "[" + currentTime + "] " + message);
    }

    @FXML
    public void onLeaveRoomClick(ActionEvent event) {
        if(timeline != null) timeline.stop(); // Tắt đồng hồ khi thoát
        // Quay lại màn detail hoặc home tùy em
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiết sản phẩm");
    }
}
