package com.auction.client.handler.profile;

import com.auction.client.SceneEngine;
import com.auction.client.controller.ProfileController;
import com.auction.client.network.Message;
import com.google.gson.Gson;
import javafx.application.Platform;

/**
 * 📡 BỘ XỬ LÝ PHẢN HỒI ĐĂNG KÝ (PROFILE MESSAGE HANDLER)
 * - Nhiệm vụ: Hứng gói tin REGISTER_RESPONSE, chạy ngầm luồng đếm giây điều hướng, giảm tải logic xử lý luồng phức tạp cho UI.
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
        // Chỉ xử lý các gói tin phản hồi liên quan đến nghiệp vụ đăng ký tài khoản mới
        if (!"REGISTER_RESPONSE".equals(msg.getType())) return;

        // Trích xuất cấu trúc JSON payload trả về thành đối tượng Record nội bộ
        RegisterResponse response = gson.fromJson(msg.getPayload(), RegisterResponse.class);

        // Đồng bộ cập nhật trạng thái UI quay lại luồng chính JavaFX Application Thread
        Platform.runLater(() -> {
            // Mở khóa lại nút bấm Đăng ký và đưa nhãn chữ về trạng thái ban đầu
            controller.getBtnRegister().setDisable(false);
            controller.getBtnRegister().setText("REGISTER");

            if (response.success()) {
                // Đăng ký thành công -> ngắt kết nối bộ lắng nghe của màn hình này để tránh rò rỉ dữ liệu (Memory Leak)
                controller.detachNetworkListener();

                String selectedRole = controller.getPendingRole();

                if ("ADMIN".equalsIgnoreCase(selectedRole)) {
                    controller.showInfo("Đăng ký Admin thành công! Đang vào trang quản trị...");

                    // Tạo một luồng chạy ngầm để delay thời gian chuyển cảnh, tránh gây đơ (Freeze) UI chính
                    new Thread(() -> {
                        try { 
                            Thread.sleep(1200); 
                        } catch (InterruptedException ignored) {}
                        
                        Platform.runLater(() -> {
                            controller.showInfo("Tài khoản Admin đã tạo! Vui lòng đăng nhập để vào Admin Panel.");
                            
                            new Thread(() -> {
                                try { 
                                    Thread.sleep(1500); 
                                } catch (InterruptedException ignored) {}
                                
                                Platform.runLater(() -> SceneEngine.changeScene(
                                        controller.getBtnRegister(),
                                        "login-view.fxml",
                                        "The Curator — Đăng nhập"
                                ));
                            }).start();
                        });
                    }).start();

                } else {
                    // Đối với phân quyền người dùng thông thường (BIDDER / SELLER) -> chuyển thẳng về màn đăng nhập
                    controller.showInfo("Đăng ký thành công! Đang chuyển về đăng nhập...");

                    new Thread(() -> {
                        try { 
                            Thread.sleep(1200); 
                        } catch (InterruptedException ignored) {}
                        
                        Platform.runLater(() -> SceneEngine.changeScene(
                                controller.getBtnRegister(),
                                "login-view.fxml",
                                "The Curator — Đăng nhập"
                        ));
                    }).start();
                }

            } else {
                // Server thông báo đăng ký thất bại (Trùng tên tài khoản hoặc email hệ thống)
                String errorMsg = response.message() != null ? response.message()
                        : "Đăng ký thất bại. Tên đăng nhập hoặc email đã tồn tại.";
                controller.showError(errorMsg);
            }
        });
    }

    /** Cấu trúc Record nội bộ bóc tách nhanh thuộc tính từ chuỗi JSON của Server */
    private record RegisterResponse(boolean success, String message) {}
}