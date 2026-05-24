package com.auction.client.controller;

import java.io.File;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;

public class DetailController implements Initializable {

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

    private Item currentItem;

    // FIX: lưu itemId riêng để filter BID_UPDATE ngay từ đầu,
    // không phụ thuộc vào currentItem (có thể chưa load xong khi BID_UPDATE đến)
    private String watchingItemId;

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

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
        client.addListener(listener);

        watchingItemId = SelectedProductSession.getInstance().getProductId();

        if (watchingItemId != null) {
            // FIX: gửi WATCH_AUCTION NGAY từ đầu để nhận BID_UPDATE realtime
            // ngay cả khi PRODUCT_DETAIL_RESPONSE chưa về
            // Server ClientHandler lưu watchingAuctionId = itemId,
            // broadcastToAuction(productId, msg) filter theo itemId → khớp
            client.send(new Message("WATCH_AUCTION",
                    gson.toJson(Map.of("auctionId", watchingItemId))));

            client.send(new Message("GET_PRODUCT_DETAIL",
                    gson.toJson(Map.of("itemId", watchingItemId))));
            setText(productNameLabel, "Đang tải...");
        } else {
            setText(productNameLabel, "Không tìm thấy sản phẩm.");
        }
    }

    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            case "PRODUCT_DETAIL_RESPONSE" -> {
                try {
                    JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                    if (!root.has("success") || !root.get("success").getAsBoolean()) {
                        Platform.runLater(() -> setText(productNameLabel, "Không tìm thấy sản phẩm."));
                        return;
                    }
                    Item item = gson.fromJson(root.get("item"), Item.class);
                    if (item == null) return;

                    Platform.runLater(() -> {
                        currentItem = item;
                        watchingItemId = item.getId(); // đảm bảo khớp với server
                        populateUI(item);
                        // KHÔNG gửi WATCH_AUCTION lại ở đây — đã gửi trong initialize()
                    });
                } catch (Exception e) {
                    System.err.println("[Detail] Lỗi parse PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
                }
            }

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();

                    // FIX: dùng watchingItemId (set ngay trong initialize) thay vì currentItem.getId()
                    // để xử lý BID_UPDATE ngay cả khi currentItem chưa được load từ server
                    if (watchingItemId == null || !watchingItemId.equals(productId)) return;

                    double newBid = dto.get("newBid").getAsDouble();

                    Platform.runLater(() -> {
                        // Cập nhật label giá hiện tại
                        if (currentPriceLabel != null) {
                            currentPriceLabel.setText(
                                    String.format("Current Bid: %,.0f VNĐ", newBid));
                        }
                        // Cập nhật object nếu đã có
                        if (currentItem != null) {
                            currentItem.setCurrentBid(newBid);
                        }
                        System.out.println("[Detail] BID_UPDATE nhận: " + newBid);
                    });
                } catch (Exception e) {
                    System.err.println("[Detail] Lỗi parse BID_UPDATE: " + e.getMessage());
                }
            }

            case "AUCTION_ENDED" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String endedItemId = dto.has("itemId") && !dto.get("itemId").isJsonNull()
                            ? dto.get("itemId").getAsString() : null;
                    // Chỉ xử lý nếu đúng phiên mình đang xem
                    if (endedItemId != null && !endedItemId.equals(watchingItemId)) return;

                    Platform.runLater(() -> {
                        if (lblStatus != null) lblStatus.setText("FINISHED");
                        System.out.println("[Detail] Phiên đấu giá kết thúc.");
                    });
                } catch (Exception e) {
                    System.err.println("[Detail] Lỗi parse AUCTION_ENDED: " + e.getMessage());
                }
            }
        }
    }

    private void populateUI(Item item) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        productNameLabel.setText(item.getName());
        currentPriceLabel.setText(
                String.format("Current Bid: %,.0f VNĐ", item.getCurrentBid()));

        setText(lblStartingPrice, String.format("%,.0f VNĐ", item.getStartingPrice()));
        setText(lblBidIncrement,  String.format("%,.0f VNĐ", item.getBidIncrement()));
        setText(lblStatus,        item.getStatus());
        setText(lblCategory,      item.getType());
        setText(lblOrigin,        item.getSellerId() != null ? item.getSellerId() : "—");
        setText(lblSeller,        item.getSellerName() != null ? item.getSellerName() : "—");
        setText(lblEndTime,       item.getEndTime() != null
                ? item.getEndTime().format(fmt) : "—");

        String desc = item.getDescription();
        setText(lblDescription, desc != null && !desc.isBlank() ? desc : item.showDetails());

        loadImage(item.getImagePath());
    }

    @FXML public void onJoinRoomClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "live-bidding-view.fxml", "Phòng Đấu Giá Trực Tiếp");
    }

    @FXML public void onBackToHomeClick(ActionEvent event) {
        client.removeListener(listener);
        SelectedProductSession.getInstance().clear();
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    @FXML public void onThumbnailClick(MouseEvent event) {
        ImageView clicked = (ImageView) event.getSource();
        if (clicked.getImage() != null) mainImageView.setImage(clicked.getImage());
        resetBorders();
        clicked.setStyle("-fx-border-color:#ffaa00; -fx-border-width:2px;");
    }

    private void resetBorders() {
        if (thumb1 != null) thumb1.setStyle("-fx-border-color:transparent;");
        if (thumb2 != null) thumb2.setStyle("-fx-border-color:transparent;");
        if (thumb3 != null) thumb3.setStyle("-fx-border-color:transparent;");
    }

    private void setText(Label label, String value) {
        if (label != null) label.setText(value != null ? value : "—");
    }

    /**
     * Load ảnh từ đường dẫn tuyệt đối.
     * Dùng File.toURI().toString() để tránh lỗi trên Windows (drive letter, khoảng trắng).
     */
    private void loadImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return;
        try {
            File imgFile = new File(imagePath);
            if (!imgFile.exists()) {
                System.err.println("[Detail] Không tìm thấy file ảnh: " + imagePath);
                return;
            }
            Image img = new Image(imgFile.toURI().toString(), true);
            mainImageView.setImage(img);
            if (thumb1 != null) thumb1.setImage(img);
            if (thumb2 != null) thumb2.setImage(img);
        } catch (Exception e) {
            System.err.println("[Detail] Lỗi load ảnh: " + e.getMessage());
        }
    }
}
