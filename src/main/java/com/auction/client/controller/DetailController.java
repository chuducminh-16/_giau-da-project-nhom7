package com.auction.client.controller;

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

    // currentItem giờ là local display state; được set từ server response
    private Item currentItem;
    private String itemId;

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();

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
        client.addListener(listener);

        // Lấy itemId từ Session (chỉ lưu ID, không lưu object)
        itemId = SelectedProductSession.getInstance().getProductId();

        if (itemId != null) {
            // Luôn fetch fresh data từ server — không dùng cached object
            client.send(new Message("GET_PRODUCT_DETAIL",
                    gson.toJson(Map.of("itemId", itemId))));
            setText(productNameLabel, "Đang tải...");
        } else {
            setText(productNameLabel, "Không tìm thấy sản phẩm.");
        }
    }

    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            // Server trả fresh product detail
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
                        populateUI(item);
                        // Đăng ký theo dõi realtime BID_UPDATE sau khi có item
                        client.send(new Message("WATCH_AUCTION",
                                gson.toJson(Map.of("auctionId", item.getId()))));
                    });
                } catch (Exception e) {
                    System.err.println("[Detail] Lỗi parse PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
                }
            }

            // Realtime bid update — cập nhật giá hiện tại
            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();
                    if (currentItem == null || !currentItem.getId().equals(productId)) return;

                    double newBid = dto.get("newBid").getAsDouble();
                    Platform.runLater(() -> {
                        currentItem.setCurrentBid(newBid);
                        currentPriceLabel.setText(
                                String.format("Current Bid: %,.0f VNĐ", newBid));
                    });
                } catch (Exception e) {
                    System.err.println("[Detail] Lỗi parse BID_UPDATE: " + e.getMessage());
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
        // itemId đã có trong session, LiveBidding sẽ dùng
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

    private void loadImage(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) return;
        try {
            Image img = new Image("file:" + imagePath, true);
            mainImageView.setImage(img);
            if (thumb1 != null) thumb1.setImage(img);
        } catch (Exception e) {
            System.err.println("[Detail] Không load được ảnh: " + e.getMessage());
        }
    }
}