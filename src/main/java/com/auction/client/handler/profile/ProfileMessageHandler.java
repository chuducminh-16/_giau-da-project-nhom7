package com.auction.client.handler.profile;

import com.auction.client.SceneEngine;
import com.auction.client.controller.ProfileController;
import com.auction.client.network.Message;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
import javafx.application.Platform;

/**
 * 📡 BỘ XỬ LÝ PHẢN HỒI ĐĂNG KÝ & HỒ SƠ (PROFILE MESSAGE HANDLER)
 * - Nhiệm vụ: Hứng gói tin REGISTER_RESPONSE và UPDATE_PROFILE_RESPONSE, xử lý logic đồng bộ dữ liệu mạng cho UI.
 */
public class ProfileMessageHandler {

    private final ProfileController controller;
    private final Gson gson = new Gson();

    public ProfileMessageHandler(ProfileController controller) {
        this.controller = controller;
    }

    /**
     * Hàm xử lý gói tin mạng trả về từ hệ thống Server đấu giá.
     */
    public void handleServerResponse(Message msg) {
        
        // =========================================================================
        // 🌟 KHỐI 1: XỬ LÝ PHẢN HỒI CẬP NHẬT HỒ SƠ CÁ NHÂN (Đã đồng bộ trường với Server)
        // =========================================================================
        if ("UPDATE_PROFILE_RESPONSE".equals(msg.getType())) {
            // Trích xuất cấu trúc JSON payload thành đối tượng Record nội bộ
            UpdateProfileResponse response = gson.fromJson(msg.getPayload(), UpdateProfileResponse.class);
            
            // Đồng bộ cập nhật trạng thái UI quay lại luồng chính JavaFX Application Thread
            Platform.runLater(() -> {
                // Mở khóa lại nút bấm lưu ở giao diện
                controller.getBtnRegister().setDisable(false);
                controller.getBtnRegister().setText("SAVE CHANGES");

                // 🔥 ĐỒNG BỘ: Kiểm tra chuỗi trạng thái "SUCCESS" khớp với trường "status" của Server
                if ("SUCCESS".equalsIgnoreCase(response.status())) {
                    
                    // 👉 TIẾN HÀNH ĐÈ DỮ LIỆU ĐÃ XÁC THỰC TỪ SERVER VÀO BỘ NHỚ TẠM USERSESSION Ở CLIENT
                    UserSession session = UserSession.getInstance();
                    session.login(
                            session.getUserId(),    // Giữ nguyên ID phân định
                            session.getUsername(),  // Giữ nguyên Username (không cho sửa)
                            response.fullName(),    // Đè họ tên mới vừa lưu thành công từ DB
                            response.email(),       // Đè email mới
                            response.phone(),       // Đè số điện thoại mới
                            response.address(),     // Đè địa chỉ mới
                            session.getRole()       // Giữ nguyên vai trò phân quyền cũ
                    );

                    // Hiển thị nhãn chữ thông báo thành công thực sự lên giao diện
                    controller.showInfo("Hệ thống đã lưu thay đổi thông tin thành công!");
                } else {
                    // Server báo lưu thất bại (Ví dụ: trùng lặp email, lỗi kết nối DB phía Server...)
                    String errorMsg = response.message() != null ? response.message() : "Cập nhật hồ sơ thất bại.";
                    controller.showError(errorMsg);
                }
            });
            return; // Xử lý xong thì kết thúc hàm
        }

        // =========================================================================
        // 🔄 KHỐI 2: XỬ LÝ PHẢN HỒI ĐĂNG KÝ TÀI KHOẢN MỚI (Mã nguồn gốc giữ nguyên)
        // =========================================================================
        if ("REGISTER_RESPONSE".equals(msg.getType())) {
            RegisterResponse response = gson.fromJson(msg.getPayload(), RegisterResponse.class);

            Platform.runLater(() -> {
                controller.getBtnRegister().setDisable(false);
                controller.getBtnRegister().setText("REGISTER");

                if (response.success()) {
                    controller.detachNetworkListener();
                    String selectedRole = controller.getPendingRole();

                    if ("ADMIN".equalsIgnoreCase(selectedRole)) {
                        controller.showInfo("Đăng ký Admin thành công! Đang vào trang quản trị...");

                        new Thread(() -> {
                            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                            
                            Platform.runLater(() -> {
                                controller.showInfo("Tài khoản Admin đã tạo! Vui lòng đăng nhập để vào Admin Panel.");
                                
                                new Thread(() -> {
                                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                                    Platform.runLater(() -> SceneEngine.changeScene(
                                            controller.getBtnRegister(),
                                            "login-view.fxml",
                                            "The Curator — Đăng nhập"
                                    ));
                                }).start();
                            });
                        }).start();

                    } else {
                        controller.showInfo("Đăng ký thành công! Đang chuyển về đăng nhập...");

                        new Thread(() -> {
                            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                            Platform.runLater(() -> SceneEngine.changeScene(
                                    controller.getBtnRegister(),
                                    "login-view.fxml",
                                    "The Curator — Đăng nhập"
                            ));
                        }).start();
                    }

                } else {
                    String errorMsg = response.message() != null ? response.message()
                            : "Đăng ký thất bại. Tên đăng nhập hoặc email đã tồn tại.";
                    controller.showError(errorMsg);
                }
            });
        }
    }

    /** Cấu trúc Record nội bộ bóc tách thuộc tính phản hồi đăng ký từ Server */
    private record RegisterResponse(boolean success, String message) {}

    /** * 📌 Cấu trúc Record nội bộ bóc tách thuộc tính phản hồi hồ sơ từ Server
     * Đã chỉnh sửa: Chuyển trường "boolean success" thành "String status" để nhận diện chính xác JSON từ Server trả xuống.
     */
    private record UpdateProfileResponse(
            String status,      // 🔄 Nhận chuỗi "SUCCESS" hoặc "FAILED" thay thế cho biến boolean cũ
            String message, 
            String fullName, 
            String email, 
            String phone, 
            String address
    ) {}
}