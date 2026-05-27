package com.auction.client.controller;

import java.util.Map;

import com.auction.client.SceneEngine;
import com.auction.client.handler.profile.ProfileMessageHandler;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
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
 * ════════════════════════════════════════════════════════════════════════
 * 🎮 PROFILE CONTROLLER — Màn hình Hồ sơ cá nhân & Đăng ký tài khoản
 * ════════════════════════════════════════════════════════════════════════
 *
 * Controller này hoạt động theo 2 chế độ tùy ngữ cảnh:
 *
 *   📋 CHẾ ĐỘ 1 — ĐĂNG KÝ (isProfileMode = false):
 *      Kích hoạt khi chưa đăng nhập. Người dùng điền thông tin tạo tài khoản mới.
 *      Nút chính: "REGISTER" | Nút phụ: "← Back to login"
 *
 *   👤 CHẾ ĐỘ 2 — HỒ SƠ CÁ NHÂN (isProfileMode = true):
 *      Kích hoạt khi đã đăng nhập và bấm nút ACC. Người dùng xem/sửa thông tin.
 *      Nút chính: "SAVE CHANGES" | Nút phụ: "← Back to Home" hoặc "← Back to Admin"
 *
 *   🔀 ĐIỀU HƯỚNG KHI BACK (isProfileMode = true):
 *      - Role = ADMIN  → quay về admin-view.fxml  (Admin Dashboard)
 *      - Role = khác   → quay về home-view.fxml   (Trang chủ Bidder/Seller)
 *
 * ════════════════════════════════════════════════════════════════════════
 */
public class ProfileController {

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 1: KHAI BÁO CÁC THÀNH PHẦN FXML
    // ════════════════════════════════════════════════════════════════════

    @FXML private TextField     fullNameField;        // Ô nhập/hiển thị Họ và tên đầy đủ
    @FXML private TextField     usernameField;        // Ô nhập/hiển thị Tên tài khoản (Username / Mã số)
    @FXML private TextField     emailField;           // Ô nhập/hiển thị địa chỉ Email
    @FXML private TextField     phoneField;           // Ô nhập/hiển thị Số điện thoại
    @FXML private PasswordField passwordField;        // Ô nhập Mật khẩu mới (để trống = giữ nguyên)
    @FXML private PasswordField confirmPasswordField; // Ô xác nhận lại Mật khẩu mới
    @FXML private TextField     addressField;         // Ô nhập/hiển thị Địa chỉ giao hàng
    @FXML private Button        btnRegister;          // Nút hành động chính: "REGISTER" hoặc "SAVE CHANGES"
    @FXML private Button        btnBackToLogin;       // Nút điều hướng phụ: quay lại trang trước
    @FXML private Label         errorLabel;           // Nhãn hiển thị thông báo lỗi hoặc thành công
    @FXML private RadioButton   radioBidder;          // Lựa chọn vai trò: Người đấu giá (Bidder)
    @FXML private RadioButton   radioSeller;          // Lựa chọn vai trò: Người bán ký gửi (Seller)
    @FXML private RadioButton   radioAdmin;           // Lựa chọn vai trò: Quản trị viên (Admin)
    @FXML private ToggleGroup   roleToggleGroup;      // Nhóm ràng buộc chỉ 1 vai trò được chọn tại 1 thời điểm

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 2: KHAI BÁO BIẾN NỘI BỘ
    // ════════════════════════════════════════════════════════════════════

    /** Công cụ chuyển đổi đối tượng Java <-> chuỗi JSON */
    private final Gson gson = new Gson();

    /** Kênh giao tiếp Socket với Server (Singleton, dùng chung toàn app) */
    private final NetworkClient client = NetworkClient.getInstance();

    /** Bộ xử lý phân tích gói tin phản hồi từ Server cho màn hình này */
    private ProfileMessageHandler messageHandler;

    /** Hàm callback lắng nghe luồng sự kiện mạng — cần lưu lại để giải phóng sau khi rời màn hình */
    private NetworkClient.MessageListener listener;

    /**
     * Lưu tạm vai trò đang chọn trước khi gửi lên Server.
     * Dùng để điều hướng đúng màn hình sau khi nhận kết quả đăng ký.
     */
    private String pendingRole = "BIDDER";

    /**
     * 🚩 CỜ TRẠNG THÁI NGỮ CẢNH (CONTEXT FLAG)
     *
     *   true  → Đang ở chế độ XEM / SỬA HỒ SƠ (người dùng đã đăng nhập, bấm ACC)
     *   false → Đang ở chế độ ĐĂNG KÝ TÀI KHOẢN MỚI (chưa đăng nhập)
     */
    private boolean isProfileMode = false;

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 3: KHỞI TẠO (INITIALIZE)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Hàm tự động chạy ngay sau khi JavaFX nạp xong file FXML.
     *
     * Luồng xử lý:
     *   1. Kết nối MessageHandler để lắng nghe phản hồi từ Server
     *   2. Kiểm tra UserSession → phân nhánh Profile Mode hoặc Register Mode
     *   3. Cấu hình UI tương ứng với từng chế độ
     *   4. Bật phím Enter cho nút hành động chính
     */
    @FXML
    public void initialize() {

        // ── Bước 1: Kết nối bộ lắng nghe mạng ──────────────────────────
        this.messageHandler = new ProfileMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerResponse(msg);
        client.addListener(listener);

        // Ẩn nhãn thông báo lỗi/thành công mặc định khi mới mở màn hình
        hideError();

        // ── Bước 2: Kiểm tra Session để xác định chế độ ─────────────────
        UserSession session = UserSession.getInstance();
        boolean isLoggedIn = (session != null
                && session.getUsername() != null
                && !session.getUsername().isBlank());

        if (isLoggedIn) {
            // ────────────────────────────────────────────────────────────
            // 👤 CHẾ ĐỘ HỒ SƠ CÁ NHÂN
            // Người dùng đã đăng nhập, vào đây để xem/chỉnh sửa thông tin
            // ────────────────────────────────────────────────────────────
            this.isProfileMode = true;

            // Đổ dữ liệu từ Session lên các ô nhập liệu
            usernameField.setText(session.getUsername());
            if (session.getFullName() != null) fullNameField.setText(session.getFullName());
            if (session.getEmail()    != null) emailField.setText(session.getEmail());
            if (session.getPhone()    != null) phoneField.setText(session.getPhone());
            if (session.getAddress()  != null) addressField.setText(session.getAddress());

            // 🔒 Khóa ô Username — không cho phép đổi tên tài khoản
            usernameField.setEditable(false);
            usernameField.setStyle(
                "-fx-background-color: #e2e8f0; -fx-text-fill: #718096;"
            );

            // 🔒 Khóa các nút chọn vai trò — không cho đổi quyền khi xem hồ sơ
            radioBidder.setDisable(true);
            radioSeller.setDisable(true);
            if (radioAdmin != null) radioAdmin.setDisable(true);

            // Tích chọn vai trò hiện tại của tài khoản đang đăng nhập
            String currentRole = session.getRole();
            if ("SELLER".equalsIgnoreCase(currentRole)) {
                radioSeller.setSelected(true);
            } else if ("ADMIN".equalsIgnoreCase(currentRole) && radioAdmin != null) {
                radioAdmin.setSelected(true);
            } else {
                radioBidder.setSelected(true);
            }

            // Cấu hình lại nhãn nút bấm cho chế độ cập nhật hồ sơ
            btnRegister.setText("SAVE CHANGES");
            btnRegister.setStyle(
                "-fx-background-color: #3182ce; -fx-text-fill: white;" +
                "-fx-font-weight: bold; -fx-font-size: 13;" +
                "-fx-background-radius: 8; -fx-cursor: hand;"
            );

            // ✅ FIX: Nhãn nút Back thay đổi tùy role
            // Admin sẽ thấy "← Back to Admin", còn lại thấy "← Back to Home"
            String role = session.getRole();
            if ("ADMIN".equalsIgnoreCase(role)) {
                btnBackToLogin.setText("← Back to Admin");
            } else {
                btnBackToLogin.setText("← Back to Home");
            }

        } else {
            // ────────────────────────────────────────────────────────────
            // 📋 CHẾ ĐỘ ĐĂNG KÝ TÀI KHOẢN MỚI
            // Chưa đăng nhập hoặc vừa đăng xuất
            // ────────────────────────────────────────────────────────────
            this.isProfileMode = false;

            // Reset toàn bộ ô nhập liệu về trống, tránh đọng dữ liệu phiên cũ
            usernameField.setText("");
            fullNameField.setText("");
            emailField.setText("");
            phoneField.setText("");
            passwordField.setText("");
            confirmPasswordField.setText("");
            addressField.setText("");

            // Mở khóa ô Username và trả lại màu trắng bình thường
            usernameField.setEditable(true);
            usernameField.setStyle(
                "-fx-background-color: white; -fx-text-fill: black;" +
                "-fx-border-color: #cbd5e0; -fx-border-radius: 6;"
            );

            // Mở khóa và hiển thị đầy đủ 3 lựa chọn vai trò bao gồm Admin
            radioBidder.setDisable(false);
            radioSeller.setDisable(false);
            radioBidder.setSelected(true); // Mặc định chọn Bidder
            if (radioAdmin != null) {
                radioAdmin.setDisable(false);
                radioAdmin.setVisible(true);
                radioAdmin.setManaged(true);
            }

            // Trả lại nhãn và màu gốc cho nút REGISTER
            btnRegister.setText("REGISTER");
            btnRegister.setStyle(
                "-fx-background-color: #1a202c; -fx-text-fill: white;" +
                "-fx-font-weight: bold; -fx-font-size: 13;" +
                "-fx-background-radius: 8; -fx-cursor: hand;"
            );

            // Trả lại nhãn nút quay về màn hình đăng nhập
            btnBackToLogin.setText("← Back to login");
        }

        // ── Bước 4: Bật phím Enter cho nút hành động chính ──────────────
        // Khi người dùng đang focus ở bất kỳ TextField nào và nhấn Enter,
        // JavaFX sẽ tự động trigger sự kiện click của nút này.
        btnRegister.setDefaultButton(true);
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 4: XỬ LÝ NÚT HÀNH ĐỘNG CHÍNH (REGISTER / SAVE CHANGES)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Xử lý khi người dùng bấm nút chính hoặc nhấn Enter.
     *
     * Phân nhánh theo isProfileMode:
     *   - true  → Gửi lệnh UPDATE_PROFILE lên Server
     *   - false → Validate rồi gửi lệnh REGISTER lên Server
     */
    @FXML
    public void onRegisterClick(ActionEvent event) {

        // ── TRƯỜNG HỢP 1: Cập nhật hồ sơ cá nhân ────────────────────────
        if (isProfileMode) {

            // Thu thập dữ liệu từ các ô nhập liệu
            String fullName = fullNameField.getText().trim();
            String email    = emailField.getText().trim();
            String phone    = phoneField.getText().trim();
            String password = passwordField.getText();
            String confirm  = confirmPasswordField.getText();
            String address  = addressField.getText().trim();

            // Kiểm tra nếu người dùng muốn đổi mật khẩu thì 2 ô phải khớp nhau
            if (!password.isEmpty() || !confirm.isEmpty()) {
                if (!password.equals(confirm)) {
                    showError("Mật khẩu mới và mật khẩu xác nhận không khớp trùng nhau!");
                    return;
                }
            }

            // Đóng gói payload JSON và gửi lệnh cập nhật lên Server
            String updatePayload = gson.toJson(Map.of(
                "username", usernameField.getText(), // Username làm khóa định danh
                "fullName", fullName,
                "email",    email,
                "phone",    phone,
                "password", password.isEmpty() ? "" : password, // Trống = giữ nguyên mật khẩu cũ
                "address",  address
            ));
            client.send(new Message("UPDATE_PROFILE", updatePayload));

            // Khóa nút lại, chờ phản hồi từ Server qua MessageHandler
            btnRegister.setDisable(true);
            btnRegister.setText("Đang lưu...");
            showInfo("Đã gửi yêu cầu lưu thay đổi thông tin lên hệ thống...");
            return;
        }

        // ── TRƯỜNG HỢP 2: Đăng ký tài khoản mới ────────────────────────
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String phone    = phoneField.getText().trim();
        String password = passwordField.getText();
        String confirm  = confirmPasswordField.getText();
        String address  = addressField.getText().trim();
        String role     = getSelectedRole();

        // Validate dữ liệu đầu vào phía client trước khi gửi lên Server
        String validationError = RegisterValidation.validate(username, email, password, confirm);
        if (validationError != null) {
            showError(validationError);
            return;
        }

        // Lưu tạm role để dùng sau khi nhận kết quả từ Server
        this.pendingRole = role;

        // Đóng gói và gửi lệnh REGISTER
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

        // Khóa nút chống spam click liên tục
        btnRegister.setDisable(true);
        btnRegister.setText("Đang xử lý...");
        hideError();
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 5: XỬ LÝ NÚT ĐIỀU HƯỚNG QUAY LẠI
    // ════════════════════════════════════════════════════════════════════

    /**
     * Xử lý khi người dùng bấm nút quay lại (← Back to ...).
     *
     * Logic điều hướng:
     *
     *   isProfileMode = false → Quay về trang Đăng nhập (login-view.fxml)
     *
     *   isProfileMode = true  → Kiểm tra role trong UserSession:
     *       - ADMIN  → Quay về Admin Dashboard (admin-view.fxml)
     *       - khác   → Quay về Trang chủ Bidder/Seller (home-view.fxml)
     *
     * ✅ FIX BUG: Trước đây luôn navigate về home-view.fxml nên Admin bị
     *    văng vào giao diện của Bidder/Seller khi bấm Back to Home.
     */
    @FXML
    protected void onBackToLoginClick(ActionEvent event) {

        // Giải phóng listener mạng của màn hình này trước khi rời đi
        // để tránh memory leak và nhận sự kiện sai màn hình
        detachNetworkListener();

        if (isProfileMode) {
            // Lấy role hiện tại từ Session để quyết định quay về đâu
            String role = UserSession.getInstance().getRole();

            if ("ADMIN".equalsIgnoreCase(role)) {
                // ✅ Admin → quay về Admin Dashboard
                SceneEngine.changeScene(event, "admin-view.fxml", "The Curator - Admin Dashboard");
            } else {
                // Bidder / Seller → quay về Trang chủ đấu giá
                SceneEngine.changeScene(event, "home-view.fxml", "The Curator — Trang chủ Đấu giá");
            }

        } else {
            // Chưa đăng nhập → quay về trang Đăng nhập
            SceneEngine.changeScene(event, "login-view.fxml", "The Curator — Đăng nhập");
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 6: CÁC HÀM TIỆN ÍCH NỘI BỘ
    // ════════════════════════════════════════════════════════════════════

    /**
     * Trích xuất vai trò (Role) từ RadioButton đang được tích chọn.
     *
     * Dùng so sánh tham chiếu đối tượng (==) thay vì getId() vì các RadioButton
     * trong FXML không nhất thiết phải khai báo thuộc tính id="" — dễ gây NullPointerException.
     */
    private String getSelectedRole() {
        RadioButton selected = (RadioButton) roleToggleGroup.getSelectedToggle();
        if (selected == null) return "BIDDER"; // Mặc định phòng hờ trường hợp null

        if (selected == radioSeller) {
            return "SELLER";
        } else if (selected == radioAdmin) {
            return "ADMIN";
        } else {
            return "BIDDER";
        }
    }

    /**
     * Giải phóng bộ lắng nghe sự kiện mạng.
     * Phải gọi hàm này trước khi rời khỏi màn hình này để tránh:
     *   - Memory leak (listener treo lơ lửng trong NetworkClient)
     *   - Nhận gói tin sai màn hình (xử lý ở màn hình cũ dù đã chuyển scene)
     */
    public void detachNetworkListener() {
        client.removeListener(listener);
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 7: ĐIỀU KHIỂN HIỂN THỊ THÔNG BÁO (ERROR LABEL)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị thông báo LỖI màu đỏ trên giao diện.
     * Dùng khi: validate thất bại, Server trả về lỗi, mật khẩu không khớp...
     */
    public void showError(String msg) {
        errorLabel.setText("⚠ " + msg);
        errorLabel.setStyle(
            "-fx-text-fill: #C0392B; -fx-font-size: 12px; -fx-font-weight: bold;"
        );
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /**
     * Hiển thị thông báo THÀNH CÔNG màu xanh lá trên giao diện.
     * Dùng khi: Server xác nhận lưu thành công, đang xử lý...
     */
    public void showInfo(String msg) {
        errorLabel.setText("✓ " + msg);
        errorLabel.setStyle(
            "-fx-text-fill: #1A6B3A; -fx-font-size: 12px; -fx-font-weight: bold;"
        );
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /**
     * Ẩn hoàn toàn nhãn thông báo và giải phóng không gian layout.
     * setManaged(false) giúp layout không chừa chỗ trống cho label khi ẩn.
     */
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 8: GETTERS CHO PHÉP HANDLER ĐỒNG BỘ UI
    // ════════════════════════════════════════════════════════════════════

    /** Trả về nút hành động chính để Handler có thể mở/khóa sau khi nhận phản hồi */
    public Button getBtnRegister() { return btnRegister; }

    /** Trả về vai trò đang chờ xử lý để Handler điều hướng đúng sau khi đăng ký thành công */
    public String getPendingRole() { return pendingRole; }
}