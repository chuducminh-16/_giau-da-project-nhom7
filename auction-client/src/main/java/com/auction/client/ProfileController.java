package com.auction.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;

public class ProfileController {
    // --- KHAI BÁO CÁC Ô NHẬP LIỆU ---
    @FXML
    private TextField fullNameField;

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private TextField phoneField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private TextField addressField;

    @FXML
    private Button registerButton;

    // --- CÁC HÀM XỬ LÝ SỰ KIỆN ---

    @FXML
    public void onRegisterClicked(ActionEvent event) {
        // Lấy dữ liệu người dùng nhập
        String fullName = fullNameField.getText();
        String username = usernameField.getText();
        String email = emailField.getText();
        String password = passwordField.getText();

        // Kiểm tra xem có để trống không
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            System.out.println("Lỗi: Vui lòng điền đầy đủ thông tin!");
            return; // Dừng lại, không chạy tiếp
        }

        System.out.println("Đang xử lý đăng ký cho tài khoản: " + username);
        // TODO: Viết code gọi API gửi thông tin đăng ký lên Server ở đây
    }

    @FXML
    public void onProfileClicked(ActionEvent event) {
        // Logic kiểm tra mật khẩu... (như bài trước)
        System.out.println("Đăng ký thành công!");
        // Đăng ký xong thì cho họ về trang Login để đăng nhập lại
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }

    @FXML
    private Button btnBackToLogin;

    // Hàm xử lý khi bấm nút Back
    @FXML
    protected void onBackToLoginClick(ActionEvent event) throws IOException {
        // Tải lại giao diện đăng nhập
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        Scene loginScene = new Scene(fxmlLoader.load());

        // Lấy cửa sổ hiện tại và đổi cảnh
        Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        currentStage.setScene(loginScene);
        currentStage.show();
    }

    @FXML
    public void onRegisterClick(ActionEvent event) {
        // 1. Logic lưu dữ liệu người dùng ở đây
        System.out.println("Đăng ký thành công!");

        // 2. Chuyển sang trang Home
        SceneEngine.changeScene(
                event,
                "home-view.fxml",
                "UET Auction - Trang chủ"
        );
    }
}
