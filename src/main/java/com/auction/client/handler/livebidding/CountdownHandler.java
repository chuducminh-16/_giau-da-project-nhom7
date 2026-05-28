package com.auction.client.handler.livebidding;

// Bắt buộc phải IMPORT Controller từ package khác sang để tương tác
import com.auction.client.controller.LiveBiddingController;
import com.auction.shared.model.Entity.Item.Item;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * THƯ MỤC: client.handler
 * NHIỆM VỤ: Quản lý vòng đời chạy của đồng hồ đếm ngược (Timeline), định dạng chuỗi thời gian
 * hiển thị ra UI và tự động dập tắt/khóa tương tác phòng khi thời gian chạm mốc số 0.
 */
public class CountdownHandler {

    // Tham chiếu ngược về Controller chính để thao tác UI
    private final LiveBiddingController mainController;
    
    // Đối tượng quản lý Timer của JavaFX chạy ngầm tuần hoàn mỗi giây
    private Timeline countdownTimeline;

    /**
     * Hàm khởi tạo liên kết
     */
    public CountdownHandler(LiveBiddingController mainController) {
        this.mainController = mainController;
    }

    /**
     * ĐỌC THỜI GIAN ĐỀ XUẤT: Lấy tổng số giây còn lại từ thông tin sản phẩm do Server gửi về để nạp vào đồng hồ.
     */
    public void startCountdownFromItem() {
        Item currentItem = mainController.getCurrentItem();
        if (currentItem == null) return;

        long seconds = currentItem.getSecondsRemaining();
        
        // Nếu sản phẩm vốn dĩ đã hết thời gian từ trước khi người dùng vào phòng
        if (seconds <= 0) {
            // FIX BUG 2: Đưa về số 0 tròn trĩnh để đồng bộ điều kiện chặn
            mainController.setSecondsRemaining(0);
            Label lblCountdown = mainController.getLblCountdown();
            if (lblCountdown != null) {
                lblCountdown.setText("ĐÃ KẾT THÚC");
                lblCountdown.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold;");
            }
            // Khóa không cho nhập liệu trả giá nữa
            if (mainController.getTxtBidAmount() != null) mainController.getTxtBidAmount().setDisable(true);
            return;
        }
        
        // Nạp số giây vào biến toàn cục của Controller và kích hoạt chạy Timer
        mainController.setSecondsRemaining(seconds);
        startCountdownTimer();
    }

    /**
     * BẮT ĐẦU CHẠY ĐỒNG HỒ: Khởi chạy luồng Timeline lặp vô hạn, trừ dần 1 giây sau mỗi chu kỳ.
     */
    public void startCountdownTimer() {
        // Nếu có một Timer cũ đang chạy dở (Ví dụ phiên vừa được gia hạn thêm thời gian), dừng nó lại trước
        if (countdownTimeline != null) countdownTimeline.stop();
        
        // Thiết lập sự kiện kích hoạt đều đặn sau mỗi 1 giây (Duration.seconds(1))
        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    long currentSeconds = mainController.getSecondsRemaining();
                    currentSeconds--; // Trừ bớt 1 giây
                    mainController.setSecondsRemaining(currentSeconds);
                    
                    // Cập nhật nhãn chữ hiển thị thời gian mới ra màn hình
                    updateLabel();
                    
                    // Khi kim đồng hồ chạm vạch đích cuối cùng
                    if (currentSeconds <= 0) {
                        mainController.setSecondsRemaining(0); // FIX BUG 2
                        countdownTimeline.stop(); // Tắt bộ đếm để giải phóng CPU
                        onAuctionEnded(); // Kích hoạt chuỗi sự kiện đóng cửa phiên đấu giá
                    }
                })
        );
        countdownTimeline.setCycleCount(Timeline.INDEFINITE); // Thiết lập chu kỳ chạy lặp lại vô hạn
        countdownTimeline.playFromStart(); // Bấm nút chạy Timer
        updateLabel(); // Gọi cập nhật giao diện lập tức không cần chờ giây đầu tiên trôi qua
    }

    /**
     * ĐỊNH DẠNG TEXT HIỂN THỊ (FORMAT TIME):
     * Biến số giây thô (Ví dụ: 3665 giây) thành dạng dễ nhìn dạng Giờ:Phút:Giây (01:01:05).
     */
    public void updateLabel() {
        Label lblCountdown = mainController.getLblCountdown();
        if (lblCountdown == null) return;

        long secondsRemaining = mainController.getSecondsRemaining();
        long h = secondsRemaining / 3600;          // Tính số giờ
        long m = (secondsRemaining % 3600) / 60;   // Tính số phút
        long s = secondsRemaining % 60;            // Tính số giây lẻ

        // Nếu còn trên 1 tiếng thì hiện đủ 3 phân vùng (HH:mm:ss), nếu dưới 1 tiếng chỉ hiện (mm:ss) cho gọn
        lblCountdown.setText(h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s));

        // HIỆU ỨNG CĂNG THẲNG: Nếu thời gian còn dưới 60 giây, đổi chữ sang màu Đỏ gắt và phóng to để kích thích người dùng
        if (secondsRemaining <= 60 && secondsRemaining > 0) {
            lblCountdown.setStyle("-fx-text-fill: #fc8181; -fx-font-size: 24; -fx-font-weight: bold;");
        }
    }

    /**
     * HẠ MÀN PHIÊN ĐẤU GIÁ (DỌN DẸP UI):
     * Được gọi ngay khi đồng hồ nhảy về số 0. Đảm bảo khóa đứng toàn bộ nút nhấn trả giá.
     */
    public void onAuctionEnded() {
        Label lblCountdown = mainController.getLblCountdown();
        if (lblCountdown != null) {
            lblCountdown.setText("HẾT GIỜ!");
            lblCountdown.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 22; -fx-font-weight: bold;");
        }
        // Khóa toàn bộ các ô nhập dữ liệu, nút bấm để chặn triệt để việc gửi giá trễ hạn lên hệ thống
        if (mainController.getTxtBidAmount() != null) mainController.getTxtBidAmount().setDisable(true);
        if (mainController.getTxtMaxBid() != null) mainController.getTxtMaxBid().setDisable(true);
        if (mainController.getBtnAutoBid() != null) mainController.getBtnAutoBid().setDisable(true);
        // Hien thi thong bao het gio tren thanh thong bao ngay lap tuc
        mainController.addLog("[HẾT GIỜ] Phiên đấu giá đã kết thúc ! Đang chờ kết quả chính thức từ máy chủ...");
    }

    /**
     * DỪNG KHẨN CẤP (ANTI MEMORY LEAK):
     * Phải gọi hàm này khi người dùng nhấn nút rời phòng đấu giá, tránh việc Timer vẫn chạy ngầm gây tốn tài nguyên.
     */
    public void stop() {
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }
    }
}
