package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.util.Map;

public class LoginController {

    // ── FXML fields ────────────────────────────────────
    @FXML private TextField     emailInput;
    @FXML private PasswordField passwordInput;
    @FXML private StackPane     rootPane;
    @FXML private ImageView     backgroundImage;
    @FXML private Button        btnSignUp;
    @FXML private Label         errorLabel;   // ← thêm vào FXML (xem bên dưới)
    @FXML private Button        loginButton;

    // ── Tools ──────────────────────────────────────────
    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    // Lưu listener để removeListener khi rời màn hình
    private final NetworkClient.MessageListener listener =
            this::handleServerResponse;

    // ── initialize: JavaFX gọi tự động sau load FXML ──
    @FXML
    public void initialize() {
        // Bind ảnh nền full màn hình
        if (backgroundImage != null && rootPane != null) {
            backgroundImage.fitWidthProperty().bind(rootPane.widthProperty());
            backgroundImage.fitHeightProperty().bind(rootPane.heightProperty());
        }

        // Đăng ký lắng nghe phản hồi từ server
        client.addListener(listener);

        hideError();

        // Cho phép bấm Enter ở ô password để login
        passwordInput.setOnAction(e -> handleLogin());
    }

    // ── Nút Sign In / Login ────────────────────────────
    @FXML
    public void onLoginClicked(ActionEvent event) {
        handleLogin();
    }

    // Tách ra hàm riêng để tái sử dụng (Enter + Click đều gọi)
    private void handleLogin() {
        String email    = emailInput.getText().trim();
        String password = passwordInput.getText();

        // 1. Validate phía client
        if (email.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập email và mật khẩu.");
            return;
        }
        if (!email.contains("@")) {
            showError("Email không hợp lệ.");
            return;
        }

        // 2. Đóng gói → JSON → gửi server
        String payload = gson.toJson(Map.of(
                "email",    email,
                "password", password
        ));
        client.send(new Message("LOGIN", payload));

        // 3. Khoá nút, xoá lỗi cũ
        setLoading(true);
        hideError();
    }

    // ── Nhận phản hồi từ server ────────────────────────
    // Chạy trên network thread → PHẢI dùng Platform.runLater
    private void handleServerResponse(Message msg) {
        if (!"LOGIN_RESPONSE".equals(msg.getType())) return;

        LoginResponse resp = gson.fromJson(msg.getPayload(),
                LoginResponse.class);
        Platform.runLater(() -> {
            setLoading(false);

            if (resp.success) {
                // ✅ Lưu user vào session
                UserSession.getInstance().login(
                        resp.userId,
                        resp.username,
                        resp.email,
                        resp.role
                );

                // Gỡ listener trước khi chuyển màn hình
                client.removeListener(listener);

                // Chuyển sang Home
                SceneEngine.changeScene(
                        loginButton,
                        "home-view.fxml",
                        "The Curator - Trang chủ"
                );
            } else {
                showError(resp.message != null
                        ? resp.message
                        : "Sai email hoặc mật khẩu.");
            }
        });
    }

    // ── Nút Sign Up ────────────────────────────────────
    @FXML
    public void onSignUpClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event,
                "profile-view.fxml",
                "The Curator - Đăng ký tài khoản");
    }

    // ── Helpers ────────────────────────────────────────
    private void showError(String msg) {
        if (errorLabel == null) return;
        errorLabel.setText("⚠ " + msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        if (errorLabel == null) return;
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private void setLoading(boolean loading) {
        if (loginButton == null) return;
        loginButton.setDisable(loading);
        loginButton.setText(loading ? "Đang đăng nhập..." : "Sign In");
    }

    // ── DTO nhận từ server ─────────────────────────────
    // Server phải gửi: {"success":true,"userId":"...","username":"...","email":"...","role":"BIDDER"}
    private record LoginResponse(
            boolean success,
            String  message,
            String  userId,
            String  username,
            String  email,
            String  role
    ) {}
}