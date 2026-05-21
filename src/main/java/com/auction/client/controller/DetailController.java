package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.shared.model.Entity.Item.Item;
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

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client.addListener(listener);

        currentItem = SelectedProductSession.getInstance().getProduct();

        if (currentItem != null) {
            populateUI(currentItem);
            client.send(new Message("WATCH_AUCTION",
                    gson.toJson(Map.of("auctionId", currentItem.getId()))));
        } else {
            productNameLabel.setText("Không tìm thấy sản phẩm.");
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
        // lblDescription dùng showDetails() làm mô tả
        setText(lblDescription,   item.showDetails());

        loadImage(item.getImagePath());
    }

    private void handleServerMessage(Message msg) {
        if (!"BID_UPDATE".equals(msg.getType())) return;
        BidUpdateDto dto = gson.fromJson(msg.getPayload(), BidUpdateDto.class);
        if (currentItem == null || !currentItem.getId().equals(dto.productId)) return;
        Platform.runLater(() -> {
            currentItem.setCurrentBid(dto.newBid);
            currentPriceLabel.setText(String.format("Current Bid: %,.0f VNĐ", dto.newBid));
        });
    }

    @FXML public void onJoinRoomClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "live-bidding-view.fxml", "Phòng Đấu Giá Trực Tiếp");
    }

    @FXML public void onBackToHomeClick(ActionEvent event) {
        client.removeListener(listener);
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

    private record BidUpdateDto(String productId, double newBid) {}
}