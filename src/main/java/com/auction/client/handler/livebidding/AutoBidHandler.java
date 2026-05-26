package com.auction.client.handler.livebidding;

// Bắt buộc phải IMPORT Controller từ package khác sang để tương tác
import com.auction.client.controller.LiveBiddingController;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.AutoBidSession;
import com.auction.client.session.UserSession;
import com.auction.client.utils.ToastNotification;
import com.auction.shared.model.Entity.Item.Item;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.Map;

/**
 * THƯ MỤC: client.handler
 * NHIỆM VỤ: Quản lý toàn bộ trạng thái, kiểm tra dữ liệu đầu vào và thực thi 
 * logic đấu giá tự động (Auto-bid) theo thời gian thực mà không làm nghẽn UI chính.
 */
public class AutoBidHandler {

    // Giữ tham chiếu đến Controller chính để có thể đọc/ghi dữ liệu lên các thành phần UI
    private final LiveBiddingController mainController;
    
    // Cờ trạng thái: Xác định tính năng Auto-bid hiện tại đang BẬT hay TẮT
    private boolean autoBidActive = false;
    
    // Ngưỡng giá tối đa mà người dùng chấp nhận chi trả cho sản phẩm này
    private double autoBidMaxPrice = 0;

    // Biến tạm lưu vết ID/Tên của đối thủ khi mạng bị bất đồng bộ (Lịch sử về trước thông tin Item)
    private String pendingAutoBidTriggerId = null;
    private String pendingAutoBidTriggerName = null;

    /**
     * Hàm khởi tạo (Constructor) - Truyền Controller chính vào để liên kết
     */
    public AutoBidHandler(LiveBiddingController mainController) {
        this.mainController = mainController;
    }

    /**
     * HÀM KHÔI PHỤC PHIÊN (RESTORE SESSION):
     * Nếu người dùng lỡ tay bấm Back quay ra ngoài danh sách rồi bấm vào lại phòng,
     * hàm này sẽ kiểm tra trong bộ nhớ xem Auto-bid cũ còn hiệu lực không để tự động bật lại.
     */
    public void restoreSession(String itemId, double lastKnownBid) {
        String myUserId = UserSession.getInstance().getUserId();
        AutoBidSession session = AutoBidSession.getInstance();
        
        // Nếu bộ nhớ Session ghi nhận User này vẫn đang kích hoạt Auto-bid cho Item này
        if (session.isActiveForProduct(myUserId, itemId)) {
            this.autoBidActive = true;
            this.autoBidMaxPrice = session.getMaxPrice();
            
            // Đồng bộ lại giá gần nhất vào Controller để tính toán bước nhảy chính xác
            mainController.setLatestBid(session.getLastKnownBid());

            // ── CẬP NHẬT LẠI GIAO DIỆN KHÔI PHỤC ──
            if (mainController.getTxtMaxBid() != null) {
                // 🔄 FIX ĐỒNG BỘ: Định dạng hiển thị dấu chấm phân tách cho ô nhập khi khôi phục session cũ
                mainController.getTxtMaxBid().setText(String.format("%,.0f", autoBidMaxPrice).replace(',', '.'));
                mainController.getTxtMaxBid().setDisable(true); // Khóa ô nhập vì đang chạy
            }
            if (mainController.getBtnAutoBid() != null) {
                updateButtonToActiveStyle(mainController.getBtnAutoBid()); // Đổi nút thành màu Đỏ (Dừng)
            }
            setAutoBidStatusBadge(true); // Đổi Badge thành màu Xanh (BẬT)
            
            // Hiển thị thanh thông tin chi tiết Auto-bid phía dưới
            if (mainController.getHboxAutoBidInfo() != null) {
                mainController.getHboxAutoBidInfo().setVisible(true);
                mainController.getHboxAutoBidInfo().setManaged(true);
            }
            if (mainController.getLblAutoBidInfo() != null) {
                mainController.getLblAutoBidInfo().setText(String.format(
                        "Tối đa: %,.0f VNĐ  —  Đã tự động khôi phục phiên cũ", autoBidMaxPrice));
            }
            mainController.addLog(String.format("⚡ Auto-bid đã được khôi phục — mức tối đa %,.0f VNĐ", autoBidMaxPrice));
        }
    }

    /**
     * HÀM ĐỔI TRẠNG THÁI (TOGGLE BUTTON):
     * Được gọi khi người dùng click vào nút Auto-bid trên màn hình chính.
     */
    public void toggleAutoBid() {
        if (autoBidActive) {
            // Nếu đang bật thì tắt đi
            deactivateAutoBid();
            mainController.addLog("⚡ Đã hủy kích hoạt tính năng Auto-bid.");
        } else {
            // Nếu đang tắt thì tiến hành kiểm tra form nhập và kích hoạt lên
            if (!validateAndActivateAutoBid()) return;
            mainController.addLog(String.format("⚡ Kích hoạt Auto-bid thành công — Tối đa: %,.0f VNĐ", autoBidMaxPrice));
        }
    }

    /**
     * KIỂM TRA & KÍCH HOẠT: Đọc dữ liệu từ ô textfield, kiểm tra tính hợp lệ về giá.
     */
    private boolean validateAndActivateAutoBid() {
        TextField txtMaxBid = mainController.getTxtMaxBid();
        if (txtMaxBid == null) return false;

        // 🔄 FIX CHÍNH: Thay vì xóa dấu phẩy, giờ ta bóc sạch toàn bộ dấu chấm phân tách hàng nghìn (.) do TextFormatter tự sinh ra khi gõ
        String raw = txtMaxBid.getText().trim().replaceAll("\\.", "");
        if (raw.isEmpty()) {
            mainController.addLog("❌ Vui lòng nhập số tiền tối đa bạn muốn trả.");
            flashInputError(txtMaxBid); // Hiệu ứng nhấp nháy viền đỏ cảnh báo
            return false;
        }

        double maxPrice;
        try {
            maxPrice = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            mainController.addLog("❌ Định dạng số không hợp lệ. Vui lòng nhập số nguyên!");
            flashInputError(txtMaxBid);
            return false;
        }

        if (maxPrice <= 0) {
            mainController.addLog("❌ Số tiền tối đa phải lớn hơn 0.");
            flashInputError(txtMaxBid);
            return false;
        }

        // Lấy thông tin sản phẩm hiện tại để so sánh giá sàn
        Item currentItem = mainController.getCurrentItem();
        double currentBid = Math.max(mainController.getLatestBid(), currentItem != null ? currentItem.getCurrentBid() : 0);

        // Giá đặt tự động tối đa bắt buộc phải lớn hơn giá hiện tại của sản phẩm
        if (maxPrice <= currentBid) {
            mainController.addLog(String.format("❌ Giá tối đa (%,.0f) phải lớn hơn giá hiện tại (%,.0f).", maxPrice, currentBid));
            flashInputError(txtMaxBid);
            return false;
        }

        // Đạt mọi điều kiện -> Lưu trạng thái bộ nhớ cục bộ
        this.autoBidMaxPrice = maxPrice;
        this.autoBidActive = true;

        // Đồng bộ lên bộ nhớ tạm toàn cục Session để bảo toàn dữ liệu khi chuyển tab
        double savedIncrement = currentItem != null && currentItem.getBidIncrement() > 0 ? currentItem.getBidIncrement() : 0;
        AutoBidSession.getInstance().activate(
                UserSession.getInstance().getUserId(),
                mainController.getItemId(),
                autoBidMaxPrice,
                currentBid,
                savedIncrement
        );

        // Cập nhật UI sang trạng thái hoạt động tích cực
        txtMaxBid.setDisable(true); // Khóa ô textfield không cho sửa lung tung khi đang chạy
        updateButtonToActiveStyle(mainController.getBtnAutoBid());
        setAutoBidStatusBadge(true);

        if (mainController.getHboxAutoBidInfo() != null) {
            mainController.getHboxAutoBidInfo().setVisible(true);
            mainController.getHboxAutoBidInfo().setManaged(true);
        }
        if (mainController.getLblAutoBidInfo() != null) {
            mainController.getLblAutoBidInfo().setText(String.format("Tối đa: %,.0f VNĐ  —  Hệ thống đang canh giá...", autoBidMaxPrice));
        }
        return true;
    }

    /**
     * TẮT TÍNH NĂNG AUTO-BID: Trả toàn bộ giao diện và bộ nhớ về trạng thái nghỉ ban đầu.
     */
    public void deactivateAutoBid() {
        this.autoBidActive = false;
        this.autoBidMaxPrice = 0;

        // Xóa sạch vết trong Session toàn cục
        AutoBidSession.getInstance().clear();

        // Mở khóa UI cho người dùng nhập lại chiến thuật mới
        if (mainController.getTxtMaxBid() != null) mainController.getTxtMaxBid().setDisable(false);
        if (mainController.getBtnAutoBid() != null) {
            mainController.getBtnAutoBid().setText("⚡ Kích hoạt");
            mainController.getBtnAutoBid().setStyle(
                    "-fx-background-color: #d69e2e; -fx-text-fill: white;" +
                    "-fx-font-size: 13; -fx-font-weight: bold;" +
                    "-fx-background-radius: 6; -fx-cursor: hand;");
        }

        setAutoBidStatusBadge(false);

        if (mainController.getHboxAutoBidInfo() != null) {
            mainController.getHboxAutoBidInfo().setVisible(false);
            mainController.getHboxAutoBidInfo().setManaged(false);
        }

        // Xóa sạch các trigger đang nằm trong hàng đợi bất đồng bộ
        this.pendingAutoBidTriggerId = null;
        this.pendingAutoBidTriggerName = null;
    }

    /**
     * THỰC THI AUTO-BID TỰ ĐỘNG ĐẶT GIÁ (HÀM CỐT LÕI):
     * Hàm này tự động chạy bất cứ khi nào Server thông báo phòng đấu giá có người trả giá mới (BID_UPDATE).
     */
    public void handleAutoBidIfNeeded(String lastBidderId, String lastBidderName) {
        // Nếu tính năng này đang tắt thì dừng lại ngay, không xử lý gì thêm
        if (!autoBidActive) return;
        
        // Lấy thông tin sản phẩm từ Controller
        Item currentItem = mainController.getCurrentItem();
        if (currentItem == null) return;

        // FIX BUG 2: Đồng hồ đếm ngược bằng 0 nghĩa là đã hạ màn, tuyệt đối không đặt giá trễ hạn
        if (mainController.getSecondsRemaining() == 0) return;

        String myUserId = UserSession.getInstance().getUserId();
        String myUsername = UserSession.getInstance().getUsername();

        // FIX BUG 3 & BUG 5: Kiểm tra xem ai đang dẫn đầu cuộc chơi để tránh tự nâng giá của chính mình
        boolean iAmLeading;
        if (lastBidderId != null && !lastBidderId.isBlank()) {
            iAmLeading = myUserId.equals(lastBidderId);
        } else {
            // Nếu server chỉ trả về Name (Do không có lịch sử ID), bóc tách chữ "(auto)" nếu có để so sánh chính xác tên
            String cleanName = lastBidderName != null ? lastBidderName.replace(" (auto)", "").trim() : "";
            iAmLeading = myUsername != null && myUsername.equals(cleanName);
        }

        // Nếu mình chính là người vừa đặt giá cao nhất -> Giữ nguyên trạng thái đứng im hưởng thụ
        if (iAmLeading) {
            if (mainController.getLblAutoBidInfo() != null) {
                mainController.getLblAutoBidInfo().setText(String.format("Tối đa: %,.0f VNĐ  —  Bạn đang dẫn đầu cuộc đua!", autoBidMaxPrice));
            }
            return;
        }

        // FIX BUG 4: Xác định bước nhảy an toàn từ Item hoặc lấy từ Session dự phòng
        double increment;
        if (currentItem.getBidIncrement() > 0) {
            increment = currentItem.getBidIncrement();
        } else if (AutoBidSession.getInstance().getBidIncrement() > 0) {
            increment = AutoBidSession.getInstance().getBidIncrement();
        } else {
            increment = 1000; // Mức sàn cứu cánh mặc định nếu toàn bộ dữ liệu bị trống
        }

        // Tính toán mức giá phản pháo: Giá cao nhất hiện tại + bước nhảy tối thiểu
        double nextBidAmt = mainController.getLatestBid() + increment;

        // Nếu mức giá đề xuất vượt quá giới hạn ví tiền mà người dùng thiết lập ban đầu -> Ngừng đấu giá tự động
        if (nextBidAmt > autoBidMaxPrice) {
            mainController.addLog(String.format("⚡ Auto-bid dừng: Giá đề xuất (%,.0f) vượt giới hạn tối đa bạn đặt (%,.0f).", nextBidAmt, autoBidMaxPrice));
            deactivateAutoBid();
            try {
                // Hiển thị thông báo Toast cảnh báo dạng Banner nổi lên góc màn hình
                Stage stage = (Stage) mainController.getLblCountdown().getScene().getWindow();
                ToastNotification.warn(stage, "⚡ Auto-bid đã dừng", String.format("Mức giá thị trường vượt quá số tiền %,.0f VNĐ giới hạn của bạn.", autoBidMaxPrice));
            } catch (Exception ignored) {}
            return;
        }

        // Đủ mọi điều kiện an toàn -> Tiến hành đóng gói và bắn gói tin đặt giá lên TCP/IP Socket Server
        mainController.addLog(String.format("🤖 Máy tính tự động đặt mức giá phản pháo: %,.0f VNĐ...", nextBidAmt));
        if (mainController.getLblAutoBidInfo() != null) {
            mainController.getLblAutoBidInfo().setText(String.format("Tối đa: %,.0f VNĐ  —  Vừa tự động đặt: %,.0f VNĐ", autoBidMaxPrice, nextBidAmt));
        }

        NetworkClient.getInstance().send(new Message("PLACE_BID", mainController.getGson().toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId", myUserId,
                "amount", nextBidAmt
        ))));
    }

    // ── CÁC HÀM TRỢ GIÚP GIAO DIỆN (UI HELPER) ──
    private void updateButtonToActiveStyle(Button btn) {
        if (btn == null) return;
        btn.setText("⏹ Dừng Auto-bid");
        btn.setStyle("-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                     "-fx-font-size: 13; -fx-font-weight: bold;" +
                     "-fx-background-radius: 6; -fx-cursor: hand;");
    }

    private void setAutoBidStatusBadge(boolean active) {
        Label lblStatus = mainController.getLblAutoBidStatus();
        if (lblStatus == null) return;
        if (active) {
            lblStatus.setText("● BẬT");
            lblStatus.setStyle("-fx-text-fill: #68d391; -fx-font-size: 11; -fx-font-weight: bold;" +
                               "-fx-background-color: #1c4532; -fx-background-radius: 20; -fx-padding: 3 10;");
        } else {
            lblStatus.setText("● TẮT");
            lblStatus.setStyle("-fx-text-fill: #718096; -fx-font-size: 11; -fx-font-weight: bold;" +
                               "-fx-background-color: #2d3748; -fx-background-radius: 20; -fx-padding: 3 10;");
        }
    }

    private void flashInputError(TextField field) {
        String original = field.getStyle();
        field.setStyle(original + "; -fx-border-color: #e53e3e; -fx-border-width: 2;");
        Timeline reset = new Timeline(new KeyFrame(Duration.seconds(1.5), e -> field.setStyle(original)));
        reset.setCycleCount(1);
        reset.play();
    }

    // Các hàm Getter và Setter giúp Controller bên ngoài thao tác đồng bộ bất đồng bộ
    public boolean isAutoBidActive() { return autoBidActive; }
    public String getPendingAutoBidTriggerId() { return pendingAutoBidTriggerId; }
    public void setPendingAutoBidTriggerId(String id) { this.pendingAutoBidTriggerId = id; }
    public String getPendingAutoBidTriggerName() { return pendingAutoBidTriggerName; }
    public void setPendingAutoBidTriggerName(String name) { this.pendingAutoBidTriggerName = name; }
}