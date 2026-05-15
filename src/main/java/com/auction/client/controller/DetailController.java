package com.auction.client.controller;

import com.auction.client.SceneEngine;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;

public class DetailController {

    // 1. Khai báo các ID
    @FXML
    private ImageView productImage;

    @FXML
    private Label productNameLabel;

    @FXML
    private Label currentPriceLabel;

    @FXML
    private TextField bidInput;

    @FXML
    private LineChart<String, Number> priceChart;

    @FXML private ImageView mainImageView;
    @FXML private ImageView thumb1, thumb2, thumb3;

    @FXML
    public void onBackToHomeClick(ActionEvent event) {
        // Gọi động cơ chuyển cảnh SceneEngine
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    @FXML
    public void onJoinRoomClick(ActionEvent event) {
        // Chuyển sang màn hình Đấu giá trực tiếp
        SceneEngine.changeScene(event, "live-bidding-view.fxml", "Phòng Đấu Giá Trực Tiếp");
    }

    @FXML
    public void onThumbnailClick(MouseEvent event) {
        // Lấy ImageView mà người dùng vừa click vào
        ImageView clickedThumbnail = (ImageView) event.getSource();

        // Lấy ảnh từ ảnh nhỏ và gán sang ảnh to
        if (clickedThumbnail.getImage() != null) {
            mainImageView.setImage(clickedThumbnail.getImage());
        }

        // Thêm hiệu ứng highlight cho ảnh đang chọn (Optional)
        resetBorders();
        clickedThumbnail.setStyle("-fx-border-color: #ffaa00; -fx-border-width: 2px;");
    }

    private void resetBorders() {
        thumb1.setStyle("-fx-border-color: transparent;");
        thumb2.setStyle("-fx-border-color: transparent;");
        thumb3.setStyle("-fx-border-color: transparent;");
    }
}
