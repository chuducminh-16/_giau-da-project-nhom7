package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.network.NetworkClient;
<<<<<<< HEAD
import com.auction.client.network.MessageListener;
=======
import com.auction.client.network.Message;
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.Map;

public class ProfileController {

    // ── Các field gắn với FXML ───────────────────────────
    @FXML private TextField     fullNameField;
    @FXML private TextField     usernameField;
    @FXML private TextField     emailField;
    @FXML private TextField     phoneField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField     addressField;
    @FXML private Button        registerButton;
    @FXML private Button        btnBackToLogin;
    @FXML private Label         errorLabel;   // thêm cái này vào FXML để hiện lỗi

    // ── Công cụ ──────────────────────────────────────────
    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    // Listener lưu lại để removeListener khi rời màn hình
    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    // ── Khởi tạo (JavaFX gọi tự động sau khi load FXML) ─
    @FXML
    public void initialize() {
        // Đăng ký lắng nghe phản hồi từ server
        client.addListener(listener);
        hideError();
    }

    // ── Xử lý bấm nút Đăng ký ───────────────────────────
    @FXML
    public void onRegisterClick(ActionEvent event) {

        // 1. Lấy dữ liệu từ form
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String phone    = phoneField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();
        String address  = addressField.getText().trim();

        // 2. Validate phía client — không gửi nếu sai
        if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin bắt buộc.");
            return;
        }
        if (!email.contains("@")) {
            showError("Email không hợp lệ.");
            return;
        }
        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }
        if (!password.equals(confirm)) {
            showError("Mật khẩu xác nhận không khớp.");
            return;
        }

        // 3. Đóng gói dữ liệu thành JSON payload
        //    Map → Gson → chuỗi JSON
        String payload = gson.toJson(Map.of(
                "fullName", fullName,
                "username", username,
                "email",    email,
                "phone",    phone,
                "password", password,
                "address",  address
        ));

        // 4. Bọc vào Message và gửi lên server qua socket
<<<<<<< HEAD
        client.send(new MessageListener("REGISTER", payload));
=======
        client.send(new Message("REGISTER", payload));
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a

        // 5. Khoá nút để tránh bấm 2 lần trong khi chờ server
        registerButton.setDisable(true);
        registerButton.setText("Đang xử lý...");
        hideError();
    }

    // ── Nhận phản hồi từ server ──────────────────────────
    //    Hàm này chạy trên thread của NetworkClient, KHÔNG phải JavaFX thread
    //    → bắt buộc dùng Platform.runLater() để cập nhật UI
<<<<<<< HEAD
    private void handleServerResponse(MessageListener msg) {
=======
    private void handleServerResponse(Message msg) {
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a

        // Chỉ xử lý message loại REGISTER_RESPONSE, bỏ qua loại khác
        if (!"REGISTER_RESPONSE".equals(msg.getType())) return;

        // Parse payload
        RegisterResponse response = gson.fromJson(
                msg.getPayload(), RegisterResponse.class
        );

        // Cập nhật UI phải chạy trên JavaFX thread
        Platform.runLater(() -> {

            // Mở lại nút dù thành công hay thất bại
            registerButton.setDisable(false);
            registerButton.setText("Đăng ký");

            if (response.success) {
                // ✅ Thành công → gỡ listener rồi về Login
                client.removeListener(listener);
                showInfo("Đăng ký thành công! Vui lòng đăng nhập.");

                // Chờ 1 giây cho user đọc thông báo rồi chuyển màn hình
                new Thread(() -> {
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    Platform.runLater(() ->
                            SceneEngine.changeScene(
                                    registerButton,          // node để lấy Stage
                                    "login-view.fxml",
                                    "The Curator — Đăng nhập"
                            )
                    );
                }).start();

            } else {
                // ❌ Thất bại → hiện lý do server trả về
                showError(response.message != null
                        ? response.message
                        : "Đăng ký thất bại. Tên đăng nhập hoặc email đã tồn tại.");
            }
        });
    }

    // ── Nút Back về Login ────────────────────────────────
    @FXML
    protected void onBackToLoginClick(ActionEvent event) {
        client.removeListener(listener); // dọn dẹp trước khi rời
        SceneEngine.changeScene(
                event,
                "login-view.fxml",
                "The Curator — Đăng nhập"
        );
    }

    // ── Helpers hiện/ẩn lỗi ─────────────────────────────
    private void showError(String msg) {
        errorLabel.setText("⚠ " + msg);
        errorLabel.setStyle("-fx-text-fill: #C0392B;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showInfo(String msg) {
        errorLabel.setText("✓ " + msg);
        errorLabel.setStyle("-fx-text-fill: #1A6B3A;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // ── Inner record: cấu trúc phản hồi từ server ───────
    //    Server phải gửi JSON dạng: {"success":true,"message":"..."}
    private record RegisterResponse(boolean success, String message) {}
}