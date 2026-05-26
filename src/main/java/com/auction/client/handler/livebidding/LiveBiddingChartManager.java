package com.auction.client.handler.livebidding;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;

/**
 * 🎯 LỚP QUẢN LÝ ĐỒ THỊ (CHART MANAGER)
 * - Mục đích: Tách biệt toàn bộ logic cấu hình, vẽ điểm và ép màu đồ thị ra khỏi Controller.
 * - Giúp Controller không bị phình to bởi các đoạn code can thiệp sâu vào CSS của JavaFX.
 */
public class LiveBiddingChartManager {

    // Đường đồ thị gốc được truyền từ FXML qua
    private final LineChart<String, Number> priceChart;
    
    // Chuỗi dữ liệu (Series) chứa các tọa độ (Thời gian, Mức giá) của biểu đồ
    private final XYChart.Series<String, Number> priceSeries;

    /**
     * Hàm khởi tạo (Constructor) - Nhận vào đồ thị từ Controller để thiết lập ban đầu
     */
    public LiveBiddingChartManager(LineChart<String, Number> priceChart) {
        this.priceChart = priceChart;
        this.priceSeries = new XYChart.Series<>();
        this.priceSeries.setName("Giá dẫn đầu (VNĐ)"); // Tên hiển thị ở khu vực chú thích
        initChart(); // Tiến hành cấu hình các thông số cơ bản cho đồ thị
    }

    /**
     * Cấu hình các thuộc tính hiển thị ban đầu của LineChart
     */
    private void initChart() {
        if (priceChart == null) return;
        
        priceChart.getData().clear();          // Xóa sạch các chuỗi dữ liệu cũ nếu có
        priceChart.getData().add(priceSeries); // Nạp chuỗi dữ liệu thực thời vào đồ thị
        priceChart.setAnimated(false);         // Tắt hiệu ứng mượt (Animation) để tránh lag khi cập nhật giá liên tục
        priceChart.setTitle("Biểu đồ diễn biến giá thời gian thực");
        priceChart.getXAxis().setLabel("Thời gian trong ngày");
        priceChart.getYAxis().setLabel("Số tiền (VNĐ)");

        // Cấu hình riêng cho trục dọc (Trục giá tiền)
        if (priceChart.getYAxis() instanceof NumberAxis numberAxis) {
            // KHÔNG ép trục Y bắt đầu từ số 0. Đồ thị sẽ tự động zoom cận cảnh 
            // quanh khoảng giá đang đấu (Ví dụ: đấu từ 1 tỷ -> 1.1 tỷ thì trục Y chỉ chạy từ 1 tỷ).
            numberAxis.setForceZeroInRange(false);
        }
        priceChart.setCreateSymbols(true); // Bật hiển thị các chấm tròn tại mỗi điểm mốc tọa độ
    }

    /**
     * Hàm thêm một điểm giá mới lên đồ thị thời gian thực
     * @param timeLabel Định dạng chuỗi giờ phút giây (HH:mm:ss) làm trục X
     * @param price     Mức giá vừa đặt làm trục Y
     */
    public void addChartPoint(String timeLabel, double price) {
        if (priceChart == null || priceSeries == null) return;

        // Bắt buộc đẩy vào luồng đồ họa UI Thread của JavaFX để tránh lỗi xung đột luồng (Thread-safe)
        Platform.runLater(() -> {
            // Tạo một node tọa độ mới
            XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(timeLabel, price);
            priceSeries.getData().add(dataPoint); // Đẩy điểm này vào chuỗi biểu đồ

            // GIỚI HẠN HIỂN THỊ: Chỉ giữ tối đa 30 điểm gần nhất trên màn hình để tránh làm chật đồ thị hoặc gây lag
            if (priceSeries.getData().size() > 30) {
                priceSeries.getData().remove(0); // Xóa điểm cũ nhất (đầu chuỗi)
            }

            // Lắng nghe khi JavaFX dựng xong Node đồ họa (chấm tròn) cho điểm dữ liệu này
            dataPoint.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    // Tạo một hộp thoại nhỏ (Tooltip) hiện ra khi người dùng di chuột vào chấm tròn
                    Tooltip tip = new Tooltip(String.format("⏱ Thời gian: %s%n💰 Mức giá chốt: %,.0f VNĐ", timeLabel, price));
                    tip.setStyle("-fx-background-color:#1a1f2e; -fx-text-fill:white; -fx-font-size:12px; -fx-padding:6 10;");
                    Tooltip.install(newNode, tip); // Gắn tooltip vào chấm tròn
                    
                    // Thực hiện ép màu nhuộm đỏ ngay khi chấm vừa được sinh ra
                    enforceRedStyling();
                }
            });

            // Ra lệnh cho JavaFX tính toán lại CSS nội bộ của đồ thị trước khi ép đè style bằng mã Java
            priceChart.applyCss();
            enforceRedStyling(); // Tiến hành ép màu nhuộm đỏ
        });
    }

    /**
     * Xóa toàn bộ các điểm đang vẽ trên đồ thị (Dùng khi cần làm sạch để nạp lại lịch sử phòng)
     */
    public void clearChart() {
        if (priceSeries != null) {
            priceSeries.getData().clear();
        }
    }

    /**
     * 🛠️ BỘ KHỐNG CHẾ MÀU SẮC ĐỒ HỌA TRỰC TIẾP
     * Duyệt qua từng thành phần của LineChart để ép mã màu đỏ (#FF3B30),
     * giải quyết triệt để lỗi file CSS bên ngoài bị cache hoặc không ăn màu.
     */
    private void enforceRedStyling() {
        if (priceChart == null || priceSeries == null) return;

        // BƯỚC 1: Tìm và ép màu cho sợi dây nối giữa các chấm (Line Stroke) sang màu đỏ
        for (Node line : priceChart.lookupAll(".chart-series-line")) {
            line.setStyle("-fx-stroke: #FF3B30 !important; -fx-stroke-width: 2.5px !important;");
        }

        // BƯỚC 2: Tìm ô chú thích (Legend Item) nằm dưới đáy đồ thị và nhuộm ĐỎ ĐẶC RUỘT hoàn toàn
        for (Node legendItem : priceChart.lookupAll(".chart-legend-item-symbol")) {
            legendItem.setStyle(
                "-fx-background-color: #FF3B30 !important; " + // Đỏ đặc
                "-fx-background-insets: 0 !important; " +
                "-fx-background-radius: 5px !important; " +    // Bo tròn nhẹ ô chú thích
                "-fx-padding: 5px !important;"
            );
        }

        // BƯỚC 3: Duyệt qua toàn bộ danh sách chấm để phân định chấm cũ (rỗng ruột) và chấm mới nhất (đặc ruột)
        int totalPoints = priceSeries.getData().size();
        for (int i = 0; i < totalPoints; i++) {
            XYChart.Data<String, Number> data = priceSeries.getData().get(i);
            Node node = data.getNode(); // Lấy đối tượng đồ họa chấm tròn
            
            if (node != null) {
                if (i == totalPoints - 1) {
                    // CHẤM CUỐI CÙNG (Giá cao nhất hiện tại): Ép màu ĐỎ ĐẶC RUỘT hoàn toàn, kích thước 10px
                    node.setStyle(
                        "-fx-background-color: #FF3B30 !important; " +
                        "-fx-background-insets: 0 !important; " +
                        "-fx-pref-width: 10px !important; " +
                        "-fx-pref-height: 10px !important;"
                    );
                } else {
                    // CÁC CHẤM LỊCH SỬ CŨ: Ép lõi trắng, viền đỏ (Tạo hiệu ứng RỖNG RUỘT), kích thước nhỏ hơn (8px)
                    node.setStyle(
                        "-fx-background-color: #FF3B30, #FFFFFF !important; " + // Lớp nền dưới đỏ, lớp đè lên trên trắng
                        "-fx-background-insets: 0, 2px !important; " +          // Lớp trắng lùi vào góc 2px tạo thành viền
                        "-fx-pref-width: 8px !important; " +
                        "-fx-pref-height: 8px !important;"
                    );
                }
            }
        }
    }
}