package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.service.UserService;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

import java.util.Map;

/**
 * Xử lý LOGIN và REGISTER.
 * LoginResult trả về cả Message lẫn User để ClientHandler lưu session.
 */
public class UserController {

    private final UserService userService = new UserService();
    private final Gson        gson        = new Gson();

    public record LoginResult(Message response, User user) {}

    // ── ĐĂNG NHẬP (ĐÃ THAY THẾ BẰNG CODE CÓ TRY-CATCH ĐỂ TRÁNH TREO MÀN HÌNH) ───────────────────
    public LoginResult handleLogin(String payload) {
        try {
            LoginDto dto = gson.fromJson(payload, LoginDto.class);
            System.out.println("[UserController] Dang xu ly dang nhap cho: " + dto.username());
            
            User user = userService.login(dto.username(), dto.password());

            if (user != null) {
                // Kiểm tra tránh lỗi NuLL cho các thuộc tính trước khi parse JSON
                String role = user.getRole() != null ? user.getRole() : "UNKNOWN";
                
                System.out.println("[UserController] Build goi tin thanh cong cho role: " + role);
                return new LoginResult(
                        new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                                "success",  true,
                                "userId",   user.getId() != null ? user.getId() : "",
                                "username", user.getUsername(),
                                "email",    user.getEmail() != null ? user.getEmail() : "",
                                "role",     role
                        ))),
                        user
                );
            }
            
            return new LoginResult(
                    new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "message", "Sai tên đăng nhập hoặc mật khẩu."
                    ))),
                    null
            );
        } catch (Exception e) {
            // In toàn bộ vết lỗi ra Terminal của Server để biết chính xác lỗi ở dòng nào
            System.err.println("=== BIEN CO: LOI HE THONG KHI DANG NHAP ===");
            e.printStackTrace(); 
            
            // Trả về false để client tắt chữ "Đang đăng nhập..." đi và hiện thông báo lỗi
            return new LoginResult(
                    new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "message", "Loi xu ly Server: " + e.getMessage()
                    ))),
                    null
            );
        }
    }

    // ── Đăng ký (Giữ nguyên không thay đổi) ───────────────────────────────────────
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

    // ── DTOs (Giữ nguyên không thay đổi) ──────────────────────────────────────────
    private record LoginDto(String username, String password) {}
    private record RegisterDto(String username, String email, String password,
                               String fullName, String phone, String address, String role) {}
}