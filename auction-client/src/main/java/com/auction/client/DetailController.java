package com.auction.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;

public class DetailController {

    // 1. Khai báo các ID TRÙNG KHỚP với những gì đã gõ trong Scene Builder
    @FXML
    private ImageView productImage;

    @FXML
    private Label productNameLabel;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private TextField bidInput;

    @FXML
    private LineChart<String, Number> priceChart;

    // 2. Các biến hỗ trợ lưu trữ dữ liệu
    private XYChart.Series<String, Number> priceSeries; // Đường vẽ trên biểu đồ
    private double currentHighestBid = 5000000;         // Giá hiện tại (Ví dụ 5 triệu)
    private int bidCount = 0;                           // Đếm số lượt bid để làm trục X

    // 3. Hàm initialize() tự động chạy ngay khi màn hình vừa mở lên
    @FXML
    public void initialize() {
        // Cài đặt đường biểu diễn giá
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Lịch sử biến động giá");
        priceChart.getData().add(priceSeries);

        // Hiển thị giá ban đầu lên giao diện
        updatePriceOnUI(currentHighestBid);
    }

    // 4. Hàm xử lý sự kiện khi bấm nút "Place Bid"
    @FXML
    protected void onPlaceBidClick() {
        String inputStr = bidInput.getText();

        try {
            // Chuyển chữ người dùng nhập thành số thực (double)
            double newBid = Double.parseDouble(inputStr);

            // Logic kiểm tra: Chỉ nhận giá cao hơn giá hiện hành
            if (newBid > currentHighestBid) {
                // (Sau này Networking, đoạn này sẽ gửi giá lên Server)
                // Hiện tại giả lập là đấu giá thành công:

                currentHighestBid = newBid; // Cập nhật lại giá cao nhất
                updatePriceOnUI(currentHighestBid); // Cập nhật Label và Biểu đồ

                bidInput.clear(); // Xóa trắng ô nhập liệu cho lần sau
                bidInput.setStyle("-fx-border-color: #DDDDDD;"); // Xóa viền đỏ nếu trước đó có lỗi
            } else {
                System.out.println("Lỗi: Giá nhập vào phải cao hơn giá hiện tại!");
                bidInput.setStyle("-fx-border-color: red;"); // Báo lỗi viền đỏ
            }
        } catch (NumberFormatException e) {
            System.out.println("Lỗi: Vui lòng nhập con số hợp lệ!");
            bidInput.setStyle("-fx-border-color: red;");
        }
    }

    // 5. Hàm dùng chung để cập nhật Label và thêm điểm ảnh vào LineChart
    private void updatePriceOnUI(double price) {
        // Cập nhật text, thêm dấu phẩy ngăn cách hàng nghìn cho đẹp
        currentPriceLabel.setText(String.format("Current Bid: %,.0f VNĐ", price));

        // Cập nhật biểu đồ
        bidCount++;
        String xAxisName = "Bid " + bidCount;
        XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(xAxisName, price);

        // Thêm điểm mới vào biểu đồ (Nó sẽ tự vẽ đường nối liền với điểm cũ)
        priceSeries.getData().add(dataPoint);
    }

    @FXML
    public void onBackToHomeClick(ActionEvent event) {
        // Gọi động cơ chuyển cảnh SceneEngine
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }
}
