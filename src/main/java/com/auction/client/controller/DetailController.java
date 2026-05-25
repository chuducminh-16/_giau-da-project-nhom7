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

    private Item   currentItem;
    private String watchingItemId;

    // FIX: giữ danh sách ảnh đã load để thumbnail click đúng ảnh
    private final Image[] loadedImages = new Image[4];

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Item.class, (JsonDeserializer<Item>) (json, typeOfT, ctx) -> {
                    JsonObject obj = json.getAsJsonObject();

                    // Log để kiểm tra dữ liệu server trả về có "type" không
                    // System.out.println("Debug JSON: " + obj.toString());

                    String type = "ART"; // Mặc định
                    if (obj.has("type") && !obj.get("type").isJsonNull()) {
                        type = obj.get("type").getAsString().toUpperCase();
                    }

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

                    JsonObject itemObj = root.getAsJsonObject("item");

                    // 1. Xác định type từ JSON trước khi deserialize
                    String type = "ART";
                    if (itemObj.has("type") && !itemObj.get("type").isJsonNull()) {
                        type = itemObj.get("type").getAsString().toUpperCase();
                    }

                    // 2. Deserialize dựa trên type đã xác định
                    Item item;
                    switch (type) {
                        case "ELECTRONICS": item = gson.fromJson(itemObj, Electronics.class); break;
                        case "VEHICLE":     item = gson.fromJson(itemObj, Vehicle.class); break;
                        default:            item = gson.fromJson(itemObj, Art.class); break;
                    }

                    // 3. Gán thủ công type vào object để chắc chắn getType() trả về đúng
                    if (item != null) {
                        item.setType(type); // Đảm bảo bạn đã thêm setType() vào lớp Item.java
                        Platform.runLater(() -> {
                            currentItem = item;
                            watchingItemId = item.getId();
                            populateUI(item);
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto     = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String  productId  = dto.get("productId").getAsString();
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

        // FIX: load tối đa 3 ảnh từ "path1|path2|path3"
        loadImages(item.getImagePath());
    }

    // ── Load tối đa 3 ảnh từ "path1|path2|path3" ─────────────────────────
    private void loadImages(String rawPath) {
        loadedImages[0] = null;
        loadedImages[1] = null;
        loadedImages[2] = null;

        if (rawPath == null || rawPath.isBlank()) {
            clearThumbs();
            return;
        }

        String[] paths = rawPath.split("\\|", -1);

        for (int i = 0; i < 4 && i < paths.length; i++) {
            String p = paths[i];
            if (p == null || p.isBlank()) continue;
            try {
                File f = new File(p);
                if (!f.exists()) continue;
                String lower = p.toLowerCase();
                if (lower.endsWith(".webp") || lower.endsWith(".jfif")) {
                    java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(f);
                    if (bi == null) continue;
                    javafx.scene.image.WritableImage wi =
                            new javafx.scene.image.WritableImage(bi.getWidth(), bi.getHeight());
                    javafx.embed.swing.SwingFXUtils.toFXImage(bi, wi);
                    loadedImages[i] = wi;
                } else {
                    loadedImages[i] = new Image(f.toURI().toString(), true);
                }
            } catch (Exception e) {
                System.err.println("[Detail] Lỗi load ảnh " + (i+1) + ": " + e.getMessage());
            }
        }

        // loadedImages[0] = ảnh to, [1][2][3] = 3 thumbnail

        if (mainImageView != null) mainImageView.setImage(loadedImages[0]);

        // Thumbnails
        ImageView[] thumbs = {thumb1, thumb2, thumb3};
        for (int i = 0; i < 3; i++) {
            if (thumbs[i] != null) thumbs[i].setImage(loadedImages[i + 1]);
        }

        resetBorders();
        // Highlight thumb1 nếu có ảnh [0] (ảnh to) — không highlight thumbnail
        // Mặc định hiển thị ảnh [0] ở main, không cần highlight thumbnail
    
    }

    private void clearThumbs() {
        if (mainImageView != null) mainImageView.setImage(null);
        if (thumb1 != null) { thumb1.setImage(null); }
        if (thumb2 != null) { thumb2.setImage(null); }
        if (thumb3 != null) { thumb3.setImage(null); }
    }

    // ── Thumbnail click: hiển thị ảnh tương ứng lên main ─────────────────
    @FXML
    public void onThumbnailClick(MouseEvent event) {
        ImageView clicked = (ImageView) event.getSource();
        resetBorders();
        clicked.setStyle("-fx-border-color:#ffaa00; -fx-border-width:2px;");

        // Tìm index của thumbnail được click
        int idx = 1;
        if (clicked == thumb2) idx = 2;
        else if (clicked == thumb3) idx = 3;

        Image img = loadedImages[idx];
        if (img != null && mainImageView != null) mainImageView.setImage(img);
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
