package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.network.NetworkClient;
import com.auction.client.network.Message;
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
    @FXML private RadioButton radioBidder;
    @FXML private RadioButton radioSeller;
    @FXML private RadioButton radioAdmin;
    @FXML private ToggleGroup roleToggleGroup;

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
        radioBidder.setSelected(true);
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
        String selectedRole = getSelectedRole();

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
                "address",  address,
                "role",     selectedRole
        ));

        // 4. Bọc vào Message và gửi lên server qua socket
        client.send(new Message("REGISTER", payload));

        // 5. Khoá nút để tránh bấm 2 lần trong khi chờ server
        registerButton.setDisable(true);
        registerButton.setText("Đang xử lý...");
        hideError();
    }
    // ── Lấy role String từ ToggleGroup ─

    private String getSelectedRole() {
        RadioButton selected = (RadioButton) roleToggleGroup.getSelectedToggle();

        if (selected == null) return "BIDDER"; // fallback an toàn

        // So sánh theo fx:id
        return switch (selected.getId()) {
            case "radioSeller" -> "SELLER";
            case "radioAdmin"  -> "ADMIN";
            default            -> "BIDDER";
        };
    }

    // ── Nhận phản hồi từ server ──────────────────────────
    //    Hàm này chạy trên thread của NetworkClient, KHÔNG phải JavaFX thread
    //    → bắt buộc dùng Platform.runLater() để cập nhật UI
    private void handleServerResponse(Message msg) {

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
                showInfo("Đăng ký thành công! Đang chuyển hướng...");

                // điều hướng theo role sau 1.2s
                String role = getSelectedRole();
                new Thread(() -> {
                    try { Thread.sleep(1200); }
                    catch (InterruptedException ignored) {}
                    Platform.runLater(() -> navigateByRole(role));
                }).start();

            } else {
                // ❌ Thất bại → hiện lý do server trả về
                showError(response.message != null
                        ? response.message
                        : "Đăng ký thất bại. Tên đăng nhập hoặc email đã tồn tại.");
            }
        });
    }
    // ── Điều hướng màn hình theo role ──

    private void navigateByRole(String role) {
        switch (role) {
            case "ADMIN" -> {
                System.out.println("Chuyển sang Admin Dashboard");
                SceneEngine.changeScene(
                        registerButton,
                        "home-view.fxml",
                        "The Curator — Admin Dashboard"
                );
            }
            case "SELLER" -> {
                System.out.println("Chuyển sang Seller Dashboard");
                SceneEngine.changeScene(
                        registerButton,
                        "manage-product-view.fxml",
                        "The Curator — Seller Dashboard"
                );
            }
            default -> {    // BIDDER
                System.out.println("Chuyển sang Home");
                SceneEngine.changeScene(
                        registerButton,
                        "home-view.fxml",
                        "The Curator — Trang chủ"
                );
            }
        }
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