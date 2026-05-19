package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.model.Product;
import com.auction.client.network.MessageListener;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

public class DetailController implements Initializable {

    // ── FXML — đã có trong detail-view.fxml ─────────────
    @FXML private Label     productNameLabel;
    @FXML private Label     currentPriceLabel;
    @FXML private ImageView mainImageView;
    @FXML private ImageView thumb1, thumb2, thumb3;

    // ── FXML — thêm mới vào detail-view.fxml ────────────
    @FXML private Label lblStartingPrice;  // Giá khởi điểm
    @FXML private Label lblBidIncrement;   // Bước giá
    @FXML private Label lblStatus;         // Tình trạng
    @FXML private Label lblCategory;       // Danh mục (= status tạm)
    @FXML private Label lblOrigin;         // Xuất xứ (= sellerId tạm)
    @FXML private Label lblSeller;         // Người bán
    @FXML private Label lblEndTime;        // Thời gian kết thúc
    @FXML private Label lblDescription;    // Mô tả (thay TextFlow)

    // ── State ────────────────────────────────────────────
    private Product currentProduct;

    // ── Tools ────────────────────────────────────────────
    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener =
            this::handleServerMessage;

    // ── initialize: lấy product từ Singleton và hiển thị ─
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client.addListener(listener);

        // Lấy sản phẩm được chọn từ Singleton
        currentProduct = SelectedProductSession.getInstance().getProduct();

        if (currentProduct != null) {
            populateUI(currentProduct);

            // Đăng ký nhận BID_UPDATE realtime cho sản phẩm này
            client.send(new MessageListener("WATCH_AUCTION",
                    gson.toJson(Map.of("auctionId",
                            currentProduct.getId()))));
        } else {
            productNameLabel.setText("Không tìm thấy sản phẩm.");
        }
    }

    // ── Đổ dữ liệu lên toàn bộ UI ───────────────────────
    private void populateUI(Product p) {
        DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        // Tên + giá
        productNameLabel.setText(p.getName());
        currentPriceLabel.setText(
                String.format("Current Bid: %,.0f VNĐ", p.getCurrentBid()));

        // GridPane thông tin chi tiết
        setText(lblStartingPrice,
                String.format("%,.0f VNĐ", p.getStartingPrice()));
        setText(lblBidIncrement,
                String.format("%,.0f VNĐ", p.getBidIncrement()));
        setText(lblStatus,  p.getStatus());
        setText(lblCategory, "Đồ vật");   // mở rộng sau khi có category
        setText(lblOrigin,   p.getSellerId() != null
                ? p.getSellerId() : "—");
        setText(lblSeller,   p.getSellerName() != null
                ? p.getSellerName() : "—");
        setText(lblEndTime,  p.getEndTime() != null
                ? p.getEndTime().format(fmt) : "—");
        setText(lblDescription, p.getDescription());

        // Ảnh chính
        loadImage(p.getImagePath());
    }

    // ── Nhận message realtime từ server ─────────────────
    private void handleServerMessage(MessageListener msg) {
        if (!"BID_UPDATE".equals(msg.getType())) return;

        BidUpdateDto dto = gson.fromJson(
                msg.getPayload(), BidUpdateDto.class);

        // Chỉ cập nhật nếu đúng sản phẩm đang xem
        if (currentProduct == null ||
                !currentProduct.getId().equals(dto.productId)) return;

        Platform.runLater(() -> {
            currentProduct.setCurrentBid(dto.newBid);
            currentPriceLabel.setText(
                    String.format("Current Bid: %,.0f VNĐ", dto.newBid));
        });
    }

    // ── Nút Join Bidding Room ────────────────────────────
    @FXML
    public void onJoinRoomClick(ActionEvent event) {
        // Product đã có trong Singleton → LiveBidding lấy trực tiếp
        client.removeListener(listener);
        SceneEngine.changeScene(event,
                "live-bidding-view.fxml", "Phòng Đấu Giá Trực Tiếp");
    }

    // ── Nút Back ─────────────────────────────────────────
    @FXML
    public void onBackToHomeClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event,
                "home-view.fxml", "The Curator - Trang chủ");
    }

    // ── Thumbnail click ──────────────────────────────────
    @FXML
    public void onThumbnailClick(MouseEvent event) {
        ImageView clicked = (ImageView) event.getSource();
        if (clicked.getImage() != null)
            mainImageView.setImage(clicked.getImage());
        resetBorders();
        clicked.setStyle("-fx-border-color:#ffaa00; -fx-border-width:2px;");
    }

    private void resetBorders() {
        if (thumb1 != null) thumb1.setStyle("-fx-border-color:transparent;");
        if (thumb2 != null) thumb2.setStyle("-fx-border-color:transparent;");
        if (thumb3 != null) thumb3.setStyle("-fx-border-color:transparent;");
    }

    // ── Helpers ──────────────────────────────────────────
    private void setText(Label label, String value) {
        if (label != null)
            label.setText(value != null ? value : "—");
    }

    private void loadImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return;
        try {
            Image img = new Image(
                    "file:" + imagePath, true); // true = background loading
            mainImageView.setImage(img);
            // Thumbnail cũng dùng ảnh đó
            if (thumb1 != null) thumb1.setImage(img);
        } catch (Exception e) {
            System.err.println("[Detail] Không load được ảnh: " + e.getMessage());
        }
    }

    // ── DTO ──────────────────────────────────────────────
    private record BidUpdateDto(String productId, double newBid) {}
}