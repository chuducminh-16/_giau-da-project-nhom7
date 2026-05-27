package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.service.UserService;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

import java.util.Map;

/**
 * 🎮 CONTROLLER QUẢN LÝ TÀI KHOẢN NGƯỜI DÙNG (USER CONTROLLER)
 * - Nhiệm vụ: Tiếp nhận payload từ ClientHandler, giải mã JSON và điều phối các dịch vụ: Đăng nhập, Đăng ký, Cập nhật Hồ sơ.
 * - Trả kết quả bọc trong đối tượng Message chuẩn để nạp qua luồng TCP Socket.
 */
public class UserController {

    private final UserService userService = new UserService();
    private final Gson           gson        = new Gson();

    /** Bản ghi phức hợp hỗ trợ ClientHandler lưu giữ thực thể User chạy Session trên RAM Server */
    public record LoginResult(Message response, User user) {}

    /**
     * 🔐 XỬ LÝ ĐĂNG NHẬP (LOGIN)
     * Đã đồng bộ bổ sung: Trả thêm fullName, phone, address về Client để nạp đầy bộ nhớ UserSession.
     */
    public LoginResult handleLogin(String payload) {
        try {
            LoginDto dto = gson.fromJson(payload, LoginDto.class);
            System.out.println("[UserController] Dang xu ly dang nhap cho: " + dto.username());
            
            // Gọi xuống tầng Service để truy vấn DB và kiểm tra thông tin tài khoản
            User user = userService.login(dto.username(), dto.password());

            if (user != null) {
                // Kiểm tra tránh lỗi NullPointerException cho các thuộc tính chuỗi trước khi đóng gói JSON
                String role = user.getRole() != null ? user.getRole() : "UNKNOWN";
                String fullName = user.getFullName() != null ? user.getFullName() : "";
                String phone = user.getPhone() != null ? user.getPhone() : "";
                String address = user.getAddress() != null ? user.getAddress() : "";
                
                System.out.println("[UserController] Build goi tin dang nhap thanh cong cho role: " + role);
                
                // Đóng gói Payload phản hồi chứa TOÀN BỘ thông tin cá nhân của người dùng
                return new LoginResult(
                        new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                                "success",  true,
                                "userId",   user.getId() != null ? user.getId() : "",
                                "username", user.getUsername(),
                                "fullName", fullName, // ➕ Bổ sung đồng bộ lên Client
                                "email",    user.getEmail() != null ? user.getEmail() : "",
                                "phone",    phone,    // ➕ Bổ sung đồng bộ lên Client
                                "address",  address,  // ➕ Bổ sung đồng bộ lên Client
                                "role",     role
                        ))),
                        user
                );
            }
            
            // Trường hợp sai thông tin tài khoản
            return new LoginResult(
                    new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "message", "Sai tên đăng nhập hoặc mật khẩu."
                    ))),
                    null
            );
        } catch (Exception e) {
            // In toàn bộ vết lỗi ra Terminal của Server để phục vụ quá trình debug dòng code lỗi
            System.err.println("=== BIEN CO: LOI HE THONG KHI DANG NHAP ===");
            e.printStackTrace(); 
            
            return new LoginResult(
                    new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "message", "Loi xu ly Server: " + e.getMessage()
                    ))),
                    null
            );
        }
    }

    /**
     * 📝 XỬ LÝ ĐĂNG KÝ TÀI KHOẢN MỚI (REGISTER)
     */
    public Message handleRegister(String payload) {
        RegisterDto dto = gson.fromJson(payload, RegisterDto.class);
        UserService.RegisterResult result = userService.register(
                dto.username(), dto.email(), dto.password(),
                dto.fullName(), dto.phone(), dto.address(), dto.role()
        );
        boolean success = result == UserService.RegisterResult.SUCCESS;
        String message = switch (result) {
            case SUCCESS         -> "Đăng ký thành công!";
            case EMAIL_EXISTS    -> "Email đã được sử dụng.";
            case USERNAME_EXISTS -> "Tên đăng nhập đã tồn tại.";
            default              -> "Lỗi hệ thống, thử lại sau.";
        };
        return new Message("REGISTER_RESPONSE", gson.toJson(Map.of(
                "success", success, "message", message
        )));
    }

    /**
     * 🔥 XỬ LÝ CẬP NHẬT HỒ SƠ CÁ NHÂN (UPDATE PROFILE)
     * Đã kết nối đồng bộ trực tiếp luồng ghi dữ liệu thực tế xuống UserService.
     */
    public Message handleUpdateProfile(String payload) {
        try {
            // Bước 1: Phân rã cục chuỗi JSON Payload nhận từ mạng ra dạng Bản đồ Map
            Map<String, String> data = gson.fromJson(payload, Map.class);
            String username = data.get("username");
            String fullName = data.get("fullName");
            String email    = data.get("email");
            String phone    = data.get("phone");
            String password = data.get("password");
            String address  = data.get("address");

            System.out.println("[UserController] Dang thuc thi cap nhat ho so cho: " + username);

            // Bước 2: 🛠️ GỌI ĐOẠN NGHIỆP VỤ XỬ LÝ XUỐNG SERVICE THỰC TẾ (Đã mở khóa kết nối)
            boolean isSuccess = userService.updateProfile(username, fullName, email, phone, password, address);

            // Bước 3 & 4: Kiểm tra kết quả phản hồi từ database để đóng gói gói tin thích hợp trả về Client
            if (isSuccess) {
                String jsonResponse = gson.toJson(Map.of(
                        "status", "SUCCESS",
                        "message", "Cập nhật thông tin hồ sơ tài khoản thành công!",
                        "fullName", fullName != null ? fullName : "",
                        "email",    email != null ? email : "",
                        "phone",    phone != null ? phone : "",
                        "address",  address != null ? address : ""
                ));
                return new Message("UPDATE_PROFILE_RESPONSE", jsonResponse);
            } else {
                // Trường hợp Service trả về false do lỗi ghi file/ghi DB
                return new Message("UPDATE_PROFILE_RESPONSE", gson.toJson(Map.of(
                        "status", "FAILED",
                        "message", "Không thể ghi dữ liệu mới vào Cơ sở dữ liệu. Vui lòng thử lại!"
                )));
            }

        } catch (Exception e) {
            System.err.println("=== BIEN CO: LOI HE THONG KHI CAP NHAT HO SO ===");
            e.printStackTrace();
            return new Message("ERROR", gson.toJson(Map.of(
                    "message", "Lỗi hệ thống Server không thể cập nhật dữ liệu: " + e.getMessage()
            )));
        }
    }

    // ── Các DTOs (Data Transfer Objects) đóng vai trò làm bộ lọc ánh xạ JSON ──
    private record LoginDto(String username, String password) {}
    private record RegisterDto(String username, String email, String password,
                               String fullName, String phone, String address, String role) {}
}