package com.auction.client.controller;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.utils.ImageUploadHandler;
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

    private Item   currentItem;
    private String watchingItemId;

    // ── ẢNH: handler riêng ────────────────────────────────────────────────
    private ImageUploadHandler imageHandler;
    // ─────────────────────────────────────────────────────────────────────

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
        // ── ẢNH: dùng mainImageView làm preview chính ────────────────────
        imageHandler = new ImageUploadHandler(client, mainImageView);
        // ─────────────────────────────────────────────────────────────────

        client.addListener(listener);
        watchingItemId = SelectedProductSession.getInstance().getProductId();

        if (watchingItemId != null) {
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
                        currentItem    = item;
                        watchingItemId = item.getId();
                        populateUI(item);
                    });
                } catch (Exception e) {
                    System.err.println("[Detail] Lỗi parse PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
                }
            }

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto    = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId  = dto.get("productId").getAsString();
                    if (watchingItemId == null || !watchingItemId.equals(productId)) return;

                    double newBid = dto.get("newBid").getAsDouble();
                    Platform.runLater(() -> {
                        if (currentPriceLabel != null)
                            currentPriceLabel.setText(String.format("Current Bid: %,.0f VNĐ", newBid));
                        if (currentItem != null) currentItem.setCurrentBid(newBid);
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
                    if (endedItemId != null && !endedItemId.equals(watchingItemId)) return;
                    Platform.runLater(() -> {
                        if (lblStatus != null) lblStatus.setText("FINISHED");
                    });
                } catch (Exception e) {
                    System.err.println("[Detail] Lỗi parse AUCTION_ENDED: " + e.getMessage());
                }
            }

            // ── ẢNH: delegate sang handler ────────────────────────────────
            case "GET_IMAGE_RESPONSE" -> imageHandler.onGetImageResponse(msg.getPayload());
            // ─────────────────────────────────────────────────────────────
        }
    }

    private void populateUI(Item item) {
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

        // ── ẢNH: load ảnh từ server ───────────────────────────────────────
        // thumb1 cũng hiển thị ảnh chính (thumbnail của ảnh duy nhất)
        String path = item.getImagePath() != null ? item.getImagePath() : "";
        imageHandler.loadFromServer(path);
        if (thumb1 != null) new ImageUploadHandler(client, thumb1).loadFromServer(path);
        // ─────────────────────────────────────────────────────────────────
    }

    // ── Thumbnail click: chỉ 1 ảnh nên click thumb1 = hiện ảnh chính ─────
    @FXML
    public void onThumbnailClick(MouseEvent event) {
        // Với 1 slot ảnh duy nhất, click thumbnail không cần làm gì thêm
        // mainImageView đã hiển thị ảnh đó rồi
        resetBorders();
        ((ImageView) event.getSource()).setStyle("-fx-border-color:#ffaa00; -fx-border-width:2px;");
    }

    private void resetBorders() {
        if (thumb1 != null) thumb1.setStyle("-fx-border-color:transparent;");
        if (thumb2 != null) thumb2.setStyle("-fx-border-color:transparent;");
        if (thumb3 != null) thumb3.setStyle("-fx-border-color:transparent;");
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

    private void setText(Label label, String value) {
        if (label != null) label.setText(value != null ? value : "—");
    }
}