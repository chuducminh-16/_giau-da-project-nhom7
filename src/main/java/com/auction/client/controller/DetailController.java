package com.auction.client.controller;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.handler.detail.DetailMessageHandler;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.utils.ImageUploadHandler;
import com.auction.shared.model.Entity.Item.Item;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;

/**
 * 🎮 LỚP ĐIỀU KHIỂN GIAO DIỆN CHI TIẾT (PRODUCT DETAIL UI CONTROLLER)
 * - Nhiệm vụ: Quản lý vòng đời hiển thị UI, tương tác bấm chuột, phím bấm của người dùng.
 * - Đã tích hợp tính năng đếm ngược thời gian thực (Real-time Countdown) đồng bộ hệ thống.
 */
public class DetailController implements Initializable {

    // ─── Các thành phần giao diện kết nối từ FXML ──────────────────────────
    @FXML private Label     productNameLabel;   
    @FXML private Label     currentPriceLabel;  
    @FXML private ImageView mainImageView;      
    @FXML private ImageView thumb1, thumb2, thumb3; 

    @FXML private Label lblStartingPrice; 
    @FXML private Label lblBidIncrement;  
    @FXML private Label lblStatus;        
    @FXML private Label lblCategory;      
    @FXML private Label lblOrigin;        
    @FXML private Label lblSeller;        
    @FXML private Label lblEndTime;       
    @FXML private Label lblDescription;   
    
    // 🎯 Nhãn hiển thị thời gian đếm ngược còn lại
    @FXML private Label lblCountdown; 

    // ─── Biến quản lý dữ liệu nội bộ ──────────────────────────────────────
    private Item   currentItem;     
    private String watchingItemId;  

    // ─── Các đối tượng Handler bổ trợ độc lập ──────────────────────────────
    private ImageUploadHandler   imageHandler;    // Xử lý nạp ảnh
    private DetailMessageHandler messageHandler;  // Bộ xử lý tin nhắn mạng mới tách
    
    private final NetworkClient client = NetworkClient.getInstance();
    private NetworkClient.MessageListener listener;
    
    // Luồng quản lý chu kỳ đếm ngược thời gian thực
    private Timeline countdownTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Khởi tạo bộ quản lý nạp ảnh nền từ server lên khung hiển thị chính
        imageHandler = new ImageUploadHandler(client, mainImageView);

        // 2. Khởi tạo liên kết với Handler tin nhắn mạng mới tách ra
        messageHandler = new DetailMessageHandler(this);
        listener = msg -> messageHandler.handleServerMessage(msg);
        client.addListener(listener);
        
        // 3. Lấy mã ID sản phẩm được lưu tạm trong Session khi click ở màn Home
        watchingItemId = SelectedProductSession.getInstance().getProductId();

        // 4. Phát lệnh đăng ký theo dõi phòng và lấy chi tiết nếu ID tệp tin hợp lệ
        if (watchingItemId != null) {
            client.send(new Message("WATCH_AUCTION", messageHandler.getGson().toJson(Map.of("auctionId", watchingItemId))));
            client.send(new Message("GET_PRODUCT_DETAIL", messageHandler.getGson().toJson(Map.of("itemId", watchingItemId))));
            setText(productNameLabel, "Đang tải...");
        } else {
            setText(productNameLabel, "Không tìm thấy sản phẩm.");
        }

        // 5. 🛠️ ĐIỀU CHỈNH TRÁNH LỖI PHÍM ENTER KHI FOCUS NHẢY VÀO NÚT BACK CHUYỂN CẢNH
        if (productNameLabel != null) {
            productNameLabel.setFocusTraversable(true); // Ép Label nhận khả năng giữ Focus bàn phím
            productNameLabel.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    onJoinRoomClick(new ActionEvent(event.getSource(), event.getTarget()));
                    event.consume(); // Nuốt trọn sự kiện để tránh lan truyền sang phần tử khác
                }
            });
        }

        // Khóa mục Focus toàn cục sau khi màn hình (Scene) đã tải hoàn tất lên ứng dụng
        Platform.runLater(() -> {
            if (productNameLabel != null) {
                productNameLabel.requestFocus(); // Ép hệ thống trỏ thẳng tiêu điểm vào nhãn chữ
                if (productNameLabel.getScene() != null) {
                    productNameLabel.getScene().addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
                        if (event.getCode() == KeyCode.ENTER) {
                            onJoinRoomClick(new ActionEvent(event.getSource(), event.getTarget()));
                            event.consume(); // Chặn đứng hoàn toàn phím Enter kích hoạt nhầm nút BACK
                        }
                    });
                }
            }
        });
    }

    /** Vẽ và phân phối toàn bộ chuỗi thuộc tính của Item lên các ô Label giao diện */
    public void populateUI(Item item) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        productNameLabel.setText(item.getName());
        currentPriceLabel.setText(String.format("Current Bid: %,.0f VNĐ", item.getCurrentBid()));

        setText(lblStartingPrice, String.format("%,.0f VNĐ", item.getStartingPrice()));
        setText(lblBidIncrement,  String.format("%,.0f VNĐ", item.getBidIncrement()));
        setText(lblStatus,        item.getStatus());
        setText(lblCategory,      item.getType());
        setText(lblOrigin,        item.getSellerId() != null ? item.getSellerId() : "—");
        setText(lblSeller,        item.getSellerName() != null ? item.getSellerName() : "—");
        setText(lblEndTime,       item.getEndTime() != null ? item.getEndTime().format(fmt) : "—");

        String desc = item.getDescription();
        setText(lblDescription, desc != null && !desc.isBlank() ? desc : item.showDetails());

        String path = item.getImagePath() != null ? item.getImagePath() : "";
        imageHandler.loadFromServer(path); 
        
        if (thumb1 != null) {
            new ImageUploadHandler(client, thumb1).loadFromServer(path);
        }

        // ⏳ BẮT ĐẦU KÍCH HOẠT ĐỒNG HỒ ĐẾM NGƯỢC THỜI GIAN THỰC
        if (item.getEndTime() != null) {
            startCountdownRealTime(item.getEndTime());
        } else {
            setText(lblCountdown, "--:--");
        }
    }

    /**
     * ⏳ Khởi chạy bộ đếm ngược thời gian thực lặp lại mỗi giây một lần
     */
    private void startCountdownRealTime(LocalDateTime endTime) {
        // Dừng đồng hồ cũ đang chạy (nếu có) để không bị chồng luồng
        if (countdownTimeline != null) {
            countdownTimeline.stop();
        }

        countdownTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(1), event -> {
            LocalDateTime now = LocalDateTime.now();
            Duration duration = Duration.between(now, endTime);

            if (duration.isNegative() || duration.isZero()) {
                setText(lblCountdown, "00:00 (Hết giờ)");
                if (lblCountdown != null) {
                    lblCountdown.setStyle("-fx-text-fill: #718096; -fx-font-weight: bold;");
                }
                countdownTimeline.stop();
            } else {
                long totalSecs = duration.getSeconds();
                long hours = totalSecs / 3600;
                long minutes = (totalSecs % 3600) / 60;
                long seconds = totalSecs % 60;

                // Định dạng hiển thị: Nếu còn trên 1 giờ thì hiện HH:MM:SS, ngược lại hiện MM:SS giống phòng đấu giá
                if (hours > 0) {
                    setText(lblCountdown, String.format("%02d:%02d:%02d", hours, minutes, seconds));
                } else {
                    setText(lblCountdown, String.format("%02d:%02d", minutes, seconds));
                }
            }
        }));

        countdownTimeline.setCycleCount(Animation.INDEFINITE);
        countdownTimeline.play();
    }

    /** Sự kiện click chuột vào các khung ảnh Thumbnail phụ phía dưới */
    @FXML
    public void onThumbnailClick(MouseEvent event) {
        resetBorders();
        // Nhuộm viền màu cam vàng nổi bật cho ô ảnh vừa được nhấn chọn
        ((ImageView) event.getSource()).setStyle("-fx-border-color:#ffaa00; -fx-border-width:2px;");
    }

    /** Đưa toàn bộ viền màu của các ô Thumbnail phụ về trạng thái trong suốt ẩn đi */
    private void resetBorders() {
        if (thumb1 != null) thumb1.setStyle("-fx-border-color:transparent;");
        if (thumb2 != null) thumb2.setStyle("-fx-border-color:transparent;");
        if (thumb3 != null) thumb3.setStyle("-fx-border-color:transparent;");
    }

    /** Nhấn nút: Tham gia vào phòng đấu giá trực tuyến thời gian thực */
    @FXML 
    public void onJoinRoomClick(ActionEvent event) {
        if (countdownTimeline != null) {
            countdownTimeline.stop(); // Hủy bộ đếm ngược giải phóng RAM
        }
        client.removeListener(listener); // Gỡ lắng nghe để giải phóng bộ nhớ màn hình cũ
        SceneEngine.changeScene(event, "live-bidding-view.fxml", "Phòng Đấu Giá Trực Tiếp");
    }

    /** Nhấn nút: Trở lại màn hình danh mục sản phẩm của Trang chủ */
    @FXML 
    public void onBackToHomeClick(ActionEvent event) {
        if (countdownTimeline != null) {
            countdownTimeline.stop(); // Hủy bộ đếm ngược giải phóng RAM
        }
        client.removeListener(listener);
        SelectedProductSession.getInstance().clear(); // Dọn dẹp cache ID sản phẩm đang xem
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // ─── ⚙️ HÀM TRỢ GIÚP TIỆN ÍCH VÀ CÁC CỬA SỔ COUPLING (GETTERS/SETTERS) ───
    private void setText(Label label, String value) {
        if (label != null) label.setText(value != null ? value : "—");
    }

    public void updateProductNameLabel(String text) { setText(productNameLabel, text); }
    public void setCurrentItem(Item item) { this.currentItem = item; }
    public Item getCurrentItem() { return this.currentItem; }
    public void setWatchingItemId(String id) { this.watchingItemId = id; }
    public String getWatchingItemId() { return this.watchingItemId; }
    
    public Label getCurrentPriceLabel() { return currentPriceLabel; }
    public Label getLblStatus() { return lblStatus; }
    public ImageUploadHandler getImageHandler() { return imageHandler; }
}