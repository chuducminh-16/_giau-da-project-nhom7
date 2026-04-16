package com.auction.client;

import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Label;
import javafx.scene.chart.XYChart;
import javafx.scene.control.TextField;

public class HelloController {

    // 1. Khai báo các thành phần đã đặt ID trong Scene Builder
    @FXML
    private LineChart<String, Number> priceChart;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private TextField priceInput;

    // 2. Tạo một "Series" để chứa dữ liệu biểu đồ
    private XYChart.Series<String, Number> priceSeries = new XYChart.Series<>();

    // 3. Hàm chạy ngay khi màn hình hiện lên
    @FXML
    public void initialize() {
        priceSeries.setName("Lịch sử giá đấu");
        priceChart.getData().add(priceSeries);

        // Thêm một điểm dữ liệu ảo ban đầu cho đẹp
        priceSeries.getData().add(new XYChart.Data<>("Bắt đầu", 0));
    }

    // 4. Hàm xử lý khi bấm nút BID
    @FXML
    protected void onBidClick() {
        String priceText = priceInput.getText();
        if (!priceText.isEmpty()) {
            double price = Double.parseDouble(priceText);

            // Cập nhật nhãn giá
            currentPriceLabel.setText("Giá hiện tại: " + priceText + " VND");

            // Thêm một điểm mới vào biểu đồ (giả lập thời gian bằng số lần click)
            String timeStamp = "Bid " + priceSeries.getData().size();
            priceSeries.getData().add(new XYChart.Data<>(timeStamp, price));

            // Xóa ô nhập sau khi bấm
            priceInput.clear();
        }
    }
}