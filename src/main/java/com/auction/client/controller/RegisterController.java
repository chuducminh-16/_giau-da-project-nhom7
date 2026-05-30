package com.auction.client.controller;

import java.util.Map;

import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.utils.RegisterValidation;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * ════════════════════════════════════════════════════════════════════════
 * 📋 REGISTER CONTROLLER — Màn hình Đăng ký Tài khoản Mới
 * ════════════════════════════════════════════════════════════════════════
 *
 * Tự xử lý phản hồi REGISTER_RESPONSE trực tiếp,
 * không phụ thuộc ProfileMessageHandler.
 *
 * Sau khi đăng ký thành công: chuyển về login-view.fxml
 *
 * ✅ FIX: Sửa lỗi dùng anonymous class initializer {{ }} với PauseTransition.
 * PauseTransition là final class → không thể subclass → compile error.
 * Giờ dùng biến thông thường thay thế.
 * ════════════════════════════════════════════════════════════════════════
 */
public class RegisterController {

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 1: KHAI BÁO CÁC PHẦN TỬ FXML
    // ════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 2: KHAI BÁO BIẾN NỘI BỘ
    // ════════════════════════════════════════════════════════════════════

    /** Công cụ chuyển đổi đối tượng Java <-> chuỗi JSON */
    private final Gson gson = new Gson();

    /** Kênh giao tiếp Socket với Server (Singleton) */
    private final NetworkClient client = NetworkClient.getInstance();

    /** Callback lắng nghe luồng mạng — lưu lại để giải phóng khi rời màn hình */
    private NetworkClient.MessageListener listener;

    /** Lưu tạm vai trò đang chọn trước khi gửi lên Server */
    private String pendingRole = "BIDDER";

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 3: KHỞI TẠO
    // ════════════════════════════════════════════════════════════════════

    @FXML
    public void initialize() {
        // Chỉ lắng nghe đúng gói REGISTER_RESPONSE, bỏ qua mọi gói khác
        this.listener = msg -> {
            if ("REGISTER_RESPONSE".equals(msg.getType())) {
                handleRegisterResponse(msg);
            }
        };
        client.addListener(listener);

        // Ẩn nhãn thông báo lỗi mặc định khi mới mở màn hình
        hideError();

        // Bật phím Enter cho nút đăng ký
        btnRegister.setDefaultButton(true);

        // Lắng nghe thay đổi role để cập nhật pendingRole kịp thời
        if (roleToggleGroup != null) {
            roleToggleGroup.selectedToggleProperty().addListener((obs, old, selected) ->
                pendingRole = getSelectedRole());
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 4: XỬ LÝ NÚT ĐĂNG KÝ
    // ════════════════════════════════════════════════════════════════════

    /**
     * Xử lý khi người dùng bấm nút ĐĂNG KÝ hoặc nhấn Enter.
     * Validate dữ liệu phía client trước, rồi gửi lệnh REGISTER lên Server.
     */
    @FXML
    protected void onRegisterClick(ActionEvent event) {
        String fullName = safe(fullNameField);
        String username = safe(usernameField);
        String email    = safe(emailField);
        String phone    = safe(phoneField);
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();
        String address  = safe(addressField);
        String role     = getSelectedRole();

        // Validate phía client
        String err = RegisterValidation.validate(fullName, username, email, phone, password, confirm);
        if (err != null) { showError(err); return; }

        // Lưu tạm role và gửi lệnh đăng ký lên Server
        pendingRole = role;
        client.send(new Message("REGISTER", gson.toJson(Map.of(
                "fullName", fullName,
                "username", username,
                "email",    email,
                "phone",    phone,
                "password", password,
                "address",  address,
                "role",     role
        ))));

        // Khóa nút chống spam click
        btnRegister.setDisable(true);
        btnRegister.setText("Đang xử lý...");
        hideError();
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 5: XỬ LÝ PHẢN HỒI TỪ SERVER
    // ════════════════════════════════════════════════════════════════════

    /**
     * Nhận và xử lý gói REGISTER_RESPONSE từ Server.
     *
     * ✅ FIX: Thay anonymous class initializer {{ }} bằng biến thông thường.
     * PauseTransition là final class → không thể subclass → dùng {{ }} gây compile error.
     */
    private void handleRegisterResponse(Message msg) {
        try {
            JsonObject root    = gson.fromJson(msg.getPayload(), JsonObject.class);
            boolean    success = root.has("success") && root.get("success").getAsBoolean();
            String     message = root.has("message") ? root.get("message").getAsString() : "";

            Platform.runLater(() -> {
                // Mở khóa nút đăng ký
                btnRegister.setDisable(false);
                btnRegister.setText("ĐĂNG KÝ");

                if (success) {
                    showInfo("Đăng ký thành công! Đang chuyển đến trang đăng nhập...");

                    // ✅ FIX: Dùng biến thông thường thay vì anonymous class {{ }}
                    PauseTransition pause = new PauseTransition(Duration.seconds(1.2));
                    pause.setOnFinished(e -> {
                        detachNetworkListener();
                        // Chuyển sang login-view.fxml
                        Stage stage = (Stage) btnRegister.getScene().getWindow();
                        try {
                            FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/com/auction/client/login-view.fxml"));
                            Scene scene = new Scene(loader.load());
                            stage.setTitle("The Curator - Đăng nhập");
                            stage.setScene(scene);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                    pause.play();

                } else {
                    // Hiện thông báo lỗi từ Server
                    showError(message.isBlank()
                            ? "Đăng ký thất bại, vui lòng thử lại."
                            : message);
                }
            });

        } catch (Exception e) {
            // Phòng hờ lỗi parse JSON
            Platform.runLater(() -> {
                btnRegister.setDisable(false);
                btnRegister.setText("ĐĂNG KÝ");
                showError("Lỗi xử lý phản hồi từ Server.");
            });
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 6: NÚT QUAY LẠI
    // ════════════════════════════════════════════════════════════════════

    /**
     * Quay về màn hình đăng nhập khi người dùng bấm nút Back.
     * Giải phóng listener mạng trước khi rời màn hình.
     */
    @FXML
    protected void onBackToLoginClick(ActionEvent event) {
        detachNetworkListener();
        com.auction.client.SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 7: CÁC HÀM TIỆN ÍCH
    // ════════════════════════════════════════════════════════════════════

    /**
     * Lấy text an toàn từ TextField, trả về chuỗi rỗng nếu field null.
     */
    private String safe(TextField field) {
        return field != null ? field.getText().trim() : "";
    }

    /**
     * Trích xuất vai trò từ RadioButton đang được chọn.
     * Dùng so sánh tham chiếu (==) thay vì getId() để tránh NullPointerException.
     */
    private String getSelectedRole() {
        if (roleToggleGroup == null) return "BIDDER";
        RadioButton selected = (RadioButton) roleToggleGroup.getSelectedToggle();
        if (selected == null)       return "BIDDER";
        if (selected == radioSeller) return "SELLER";
        if (selected == radioAdmin)  return "ADMIN";
        return "BIDDER";
    }

    /**
     * Giải phóng listener mạng.
     * Phải gọi trước khi rời màn hình để tránh memory leak.
     */
    public void detachNetworkListener() {
        client.removeListener(listener);
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 8: ĐIỀU KHIỂN HIỂN THỊ THÔNG BÁO
    // ════════════════════════════════════════════════════════════════════

    /** Hiển thị thông báo lỗi màu đỏ */
    public void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #C0392B; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /** Hiển thị thông báo thành công màu xanh lá */
    public void showInfo(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-text-fill: #1A6B3A; -fx-font-weight: bold;");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /** Ẩn hoàn toàn nhãn thông báo */
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 9: GETTERS
    // ════════════════════════════════════════════════════════════════════

    public Button getBtnRegister() { return btnRegister; }
    public String getPendingRole() { return pendingRole; }
}