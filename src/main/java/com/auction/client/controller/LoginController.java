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

    @FXML private TextField     emailInput;   // dùng cho username
    @FXML private PasswordField passwordInput;
    @FXML private StackPane     rootPane;
    @FXML private ImageView     backgroundImage;
    @FXML private Button        btnSignUp;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    @FXML
    public void initialize() {
        if (backgroundImage != null && rootPane != null) {
            backgroundImage.fitWidthProperty().bind(rootPane.widthProperty());
            backgroundImage.fitHeightProperty().bind(rootPane.heightProperty());
        }
        client.addListener(listener);
        hideError();
        passwordInput.setOnAction(e -> handleLogin());
    }

    @FXML
    public void onLoginClicked(ActionEvent event) {
        handleLogin();
    }

    private void handleLogin() {
        String username = emailInput.getText().trim();
        String password = passwordInput.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập tên đăng nhập và mật khẩu.");
            return;
        }

        String payload = gson.toJson(Map.of(
                "username", username,
                "password", password
        ));
        client.send(new Message("LOGIN", payload));
        setLoading(true);
        hideError();
    }

    private void handleServerResponse(Message msg) {
        if (!"LOGIN_RESPONSE".equals(msg.getType())) return;

        LoginResponse resp = gson.fromJson(msg.getPayload(), LoginResponse.class);
        Platform.runLater(() -> {
            setLoading(false);

            if (resp.success) {
                // Lưu session
                UserSession.getInstance().login(
                        resp.userId,
                        resp.username,
                        resp.email,
                        resp.role
                );
                client.removeListener(listener);

                // ── PHÂN HƯỚNG THEO ROLE ──────────────────────────────────
                // ADMIN  → admin-view.fxml
                // Còn lại → home-view.fxml
                if ("ADMIN".equalsIgnoreCase(resp.role)) {
                    SceneEngine.changeScene(
                            loginButton,
                            "admin-view.fxml",
                            "The Curator - Admin Dashboard"
                    );
                } else {
                    SceneEngine.changeScene(
                            loginButton,
                            "home-view.fxml",
                            "The Curator - Trang chủ"
                    );
                }

            } else {
                showError(resp.message != null ? resp.message : "Sai tên đăng nhập hoặc mật khẩu.");
            }
        });
    }

    @FXML
    public void onSignUpClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "profile-view.fxml", "The Curator - Đăng ký tài khoản");
    }

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

    private record LoginResponse(
            boolean success, String message,
            String userId, String username, String email, String role) {}
}
