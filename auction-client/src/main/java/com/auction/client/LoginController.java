package com.auction.client;

import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField emailInput;

    @FXML
    private PasswordField passwordInput;

    @FXML
    protected void onLoginClick(ActionEvent event) {
        String email = emailInput.getText();
        String password = passwordInput.getText();

        // Validate cơ bản
        if (email.isEmpty() || password.isEmpty()) {
            System.out.println("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        System.out.println("Đang đăng nhập với: " + email);

        // TODO: Gửi request lên Server kiểm tra DB ở đây

        // Giả lập đăng nhập thành công và chuyển sang màn hình Chi tiết đấu giá
        try {
            NavigationUtils.switchScene(event, "detail-view.fxml", "UET Auction - Đấu giá trực tiếp");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Lỗi chuyển màn hình: " + e.getMessage());
        }
    }

    @FXML
    public void onLoginClicked(ActionEvent event) {
        // Giả sử đăng nhập thành công
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    @FXML
    public void onGoToRegister(ActionEvent event) {
        SceneEngine.changeScene(event, "profile-view.fxml", "The Curator - Đăng ký tài khoản");
    }
}