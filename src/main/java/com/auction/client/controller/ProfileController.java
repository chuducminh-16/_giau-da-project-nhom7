package com.auction.client.controller;

import java.util.Map;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.google.gson.Gson;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

public class ProfileController {

    // ── Các field gắn với FXML ───────────────────────────
    @FXML private TextField     fullNameField;
    @FXML private TextField     usernameField;
    @FXML private TextField     emailField;
    @FXML private TextField     phoneField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField     addressField;
    @FXML private Button        btnRegister;       // ← khớp fx:id="btnRegister" trong FXML
    @FXML private Button        btnBackToLogin;
    @FXML private Label         errorLabel;
    @FXML private RadioButton   radioBidder;
    @FXML private RadioButton   radioSeller;
    @FXML private RadioButton   radioAdmin;
    @FXML private ToggleGroup   roleToggleGroup;

    // ── Công cụ ──────────────────────────────────────────
    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    // ── Khởi tạo ─────────────────────────────────────────
    @FXML
    public void initialize() {
        client.addListener(listener);
        hideError();
        radioBidder.setSelected(true);
    }

    // ── Xử lý bấm nút Đăng ký ────────────────────────────
    @FXML
    public void onRegisterClick(ActionEvent event) {

        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String phone    = phoneField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();
        String address  = addressField.getText().trim();
        String role     = getSelectedRole();

        // Validate phía client
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

        String payload = gson.toJson(Map.of(
                "fullName", fullName,
                "username", username,
                "email",    email,
                "phone",    phone.isEmpty() ? "" : phone,
                "password", password,
                "address",  address.isEmpty() ? "" : address,
                "role",     role
        ));

        client.send(new Message("REGISTER", payload));

        // Khoá nút chờ server phản hồi
        btnRegister.setDisable(true);
        btnRegister.setText("Đang xử lý...");
        hideError();
    }

    private String getSelectedRole() {
        RadioButton selected = (RadioButton) roleToggleGroup.getSelectedToggle();
        if (selected == null) return "BIDDER";
        return switch (selected.getId()) {
            case "radioSeller" -> "SELLER";
            case "radioAdmin"  -> "ADMIN";
            default            -> "BIDDER";
        };
    }

    // ── Nhận phản hồi từ server ──────────────────────────
    private void handleServerResponse(Message msg) {
        if (!"REGISTER_RESPONSE".equals(msg.getType())) return;

        RegisterResponse response = gson.fromJson(msg.getPayload(), RegisterResponse.class);

        Platform.runLater(() -> {
            // Khôi phục nút
            btnRegister.setDisable(false);
            btnRegister.setText("REGISTER");

            if (response.success) {
                client.removeListener(listener);
                showInfo("Đăng ký thành công! Đang chuyển về đăng nhập...");

                // Delay nhỏ rồi chuyển về Login
                new Thread(() -> {
                    try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                    Platform.runLater(() ->
                        SceneEngine.changeScene(
                            btnRegister,
                            "login-view.fxml",
                            "The Curator — Đăng nhập"
                        )
                    );
                }).start();

            } else {
                String msg2 = response.message != null ? response.message
                        : "Đăng ký thất bại. Tên đăng nhập hoặc email đã tồn tại.";
                showError(msg2);
            }
        });
    }

    // ── Nút Back về Login ────────────────────────────────
    @FXML
    protected void onBackToLoginClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator — Đăng nhập");
    }

    // ── Helpers ──────────────────────────────────────────
    private void showError(String msg) {
        errorLabel.setText("⚠ " + msg);
        errorLabel.setStyle("-fx-text-fill: #C0392B; -fx-font-size: 12px;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void showInfo(String msg) {
        errorLabel.setText("✓ " + msg);
        errorLabel.setStyle("-fx-text-fill: #1A6B3A; -fx-font-size: 12px;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private record RegisterResponse(boolean success, String message) {}
}