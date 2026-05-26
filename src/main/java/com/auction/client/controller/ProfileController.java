package com.auction.client.controller;

import java.util.Map;

import com.auction.client.SceneEngine;
import com.auction.client.handler.profile.ProfileMessageHandler;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.utils.RegisterValidation;
import com.google.gson.Gson;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;

/**
 * 🎮 LỚP ĐIỀU KHIỂN GIAO DIỆN ĐĂNG KÝ (PROFILE / REGISTER UI CONTROLLER)
 * - Nhiệm vụ: Thu thập thông tin từ Form nhập liệu, điều khiển trạng thái hiển thị của các nút bấm và nhãn thông báo lỗi.
 */
public class ProfileController {

    // ── Các linh kiện thành phần kết nối trực tiếp từ tệp tin FXML ─────────
    @FXML private TextField     fullNameField;
    @FXML private TextField     usernameField;
    @FXML private TextField     emailField;
    @FXML private TextField     phoneField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private TextField     addressField;
    @FXML private Button        btnRegister;
    @FXML private Button        btnBackToLogin;
    @FXML private Label         errorLabel;
    @FXML private RadioButton   radioBidder;
    @FXML private RadioButton   radioSeller;
    @FXML private RadioButton   radioAdmin;
    @FXML private ToggleGroup   roleToggleGroup;

    // ── Công cụ bổ trợ giao tiếp hạ tầng và xử lý gói mạng ────────────────
    private final Gson gson = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();
    
    private ProfileMessageHandler messageHandler;
    private NetworkClient.MessageListener listener;

    // Lưu trữ phân quyền đã lựa chọn để xử lý chuyển hướng sau khi Server phản hồi thành công
    private String pendingRole = "BIDDER";

    /**
     * Hàm tự động khởi chạy chu kỳ sống ngay sau khi tầng giao diện FXML nạp thành công.
     */
    @FXML
    public void initialize() {
        // Khởi tạo liên kết chặt chẽ tới Message Handler độc lập mới tách
        this.messageHandler = new ProfileMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerResponse(msg);
        client.addListener(listener);
        
        hideError();
        radioBidder.setSelected(true); // Đặt mặc định lựa chọn vai trò là Người đấu giá
    }

    /**
     * Sự kiện nút bấm: Bắt đầu xử lý khi người dùng nhấn chọn nút "Đăng ký".
     */
    @FXML
    public void onRegisterClick(ActionEvent event) {
        // Thu thập chuỗi ký tự thô từ Form nhập liệu giao diện
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String phone    = phoneField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();
        String address  = addressField.getText().trim();
        String role     = getSelectedRole();

        // 🛡️ Gọi lớp tiện ích độc lập để thực hiện kiểm tra tính hợp lệ dữ liệu đầu vào (Client-side validation)
        String validationError = RegisterValidation.validate(username, email, password, confirm);
        if (validationError != null) {
            showError(validationError);
            return;
        }

        // Lưu tạm vai trò phân quyền người dùng trước khi gửi lên mạng
        this.pendingRole = role;

        // Đóng gói dữ liệu Map thành chuỗi JSON Payload chuẩn mực
        String payload = gson.toJson(Map.of(
                "fullName", fullName,
                "username", username,
                "email",    email,
                "phone",    phone.isEmpty() ? "" : phone,
                "password", password,
                "address",  address.isEmpty() ? "" : address,
                "role",     role
        ));

        // Phát tín hiệu đăng ký tài khoản lên Server qua cổng NetworkClient
        client.send(new Message("REGISTER", payload));

        // Tạm thời vô hiệu hóa nút bấm đăng ký để ngăn chặn tình trạng người dùng click Spam liên tục
        btnRegister.setDisable(true);
        btnRegister.setText("Đang xử lý...");
        hideError();
    }

    /** Trích xuất lấy mã ID String của RadioButton phân quyền đang được chọn */
    private String getSelectedRole() {
        RadioButton selected = (RadioButton) roleToggleGroup.getSelectedToggle();
        if (selected == null) return "BIDDER";
        return switch (selected.getId()) {
            case "radioSeller" -> "SELLER";
            case "radioAdmin"  -> "ADMIN";
            default            -> "BIDDER";
        };
    }

    /**
     * Sự kiện nút bấm: Quay trở về màn hình giao diện Đăng nhập hệ thống (← BACK).
     */
    @FXML
    protected void onBackToLoginClick(ActionEvent event) {
        detachNetworkListener();
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator — Đăng nhập");
    }

    /** Hàm tiện ích hỗ trợ giải phóng bộ lắng nghe sự kiện mạng của màn hình này */
    public void detachNetworkListener() {
        client.removeListener(listener);
    }

    // ─── ⚙️ CÁC TIỆN ÍCH ĐIỀU KHIỂN HIỂN THỊ TRẠNG THÁI NHÃN CHỮ LỖI UI ────────

    public void showError(String msg) {
        errorLabel.setText("⚠ " + msg);
        errorLabel.setStyle("-fx-text-fill: #C0392B; -fx-font-size: 12px;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    public void showInfo(String msg) {
        errorLabel.setText("✓ " + msg);
        errorLabel.setStyle("-fx-text-fill: #1A6B3A; -fx-font-size: 12px;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // ─── ⚙️ CÁC CỬA SỔ ACCESSORS (GETTERS) CHO PHÉP HANDLER ĐỒNG BỘ UI ───────
    public Button getBtnRegister() { return btnRegister; }
    public String getPendingRole() { return pendingRole; }
}