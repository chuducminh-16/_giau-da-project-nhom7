package com.auction.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

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
    public void onBackToLoginClicked(ActionEvent event) {
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }
}
