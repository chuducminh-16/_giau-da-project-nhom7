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

    // ── Đăng nhập ─────────────────────────────────────
    public LoginResult handleLogin(String payload) {
        LoginDto dto = gson.fromJson(payload, LoginDto.class);
        User user = userService.login(dto.username(), dto.password());

        if (user != null) {
            return new LoginResult(
                    new Message("LOGIN_RESPONSE", gson.toJson(Map.of(
                            "success",  true,
                            "userId",   user.getId(),
                            "username", user.getUsername(),
                            "email",    user.getEmail() != null ? user.getEmail() : "",
                            "role",     user.getRole()
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
    }

    // ── Đăng ký ───────────────────────────────────────
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

    // ── DTOs ──────────────────────────────────────────
    // Dùng username (khớp với ClientHandler cũ), không phải email
    private record LoginDto(String username, String password) {}
    private record RegisterDto(String username, String email, String password,
                               String fullName, String phone, String address, String role) {}
}