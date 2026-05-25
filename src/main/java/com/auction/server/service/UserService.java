package com.auction.server.service;

import java.util.UUID;

import com.auction.server.dao.user.UserFindDAO;
import com.auction.server.dao.user.UserSaveDAO;
import com.auction.shared.model.Entity.User.Admin;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

public class UserService {

    private final UserFindDAO findDAO = new UserFindDAO();
    private final UserSaveDAO saveDAO = new UserSaveDAO();

    // ══════════════════════════════════════════
    // ENUM KẾT QUẢ ĐĂNG KÝ
    // ══════════════════════════════════════════
    public enum RegisterResult {
        SUCCESS,
        EMAIL_EXISTS,
        USERNAME_EXISTS,
        ERROR
    }

    // ══════════════════════════════════════════
    // ĐĂNG KÝ
    // ══════════════════════════════════════════
    public RegisterResult register(String username, String email, String password,
                                   String fullName, String phone, String address,
                                   String role) {
        try {
            // 1. Validate đầu vào
            if (username == null || username.isBlank()) {
                System.out.println("[UserService] REGISTER FAIL: username rỗng");
                return RegisterResult.ERROR;
            }
            if (email == null || !email.contains("@")) {
                System.out.println("[UserService] REGISTER FAIL: email không hợp lệ");
                return RegisterResult.ERROR;
            }
            if (password == null || password.length() < 6) {
                System.out.println("[UserService] REGISTER FAIL: password quá ngắn");
                return RegisterResult.ERROR;
            }

            // 2. Kiểm tra username đã tồn tại chưa
            User existing = findDAO.findByUsername(username);
            if (existing != null) {
                System.out.println("[UserService] REGISTER FAIL: username đã tồn tại: " + username);
                return RegisterResult.USERNAME_EXISTS;
            }

            // 3. Tạo user theo role
            String id = UUID.randomUUID().toString().substring(0, 8);
            User newUser;
            String normalizedRole = (role != null) ? role.toUpperCase() : "BIDDER";

            switch (normalizedRole) {
                case "SELLER" -> newUser = new Seller(id, username, email, password, 5.0);
                case "ADMIN"  -> newUser = new Admin(id, username, email, password, 1);
                default       -> newUser = new Bidder(id, username, email, password, 1000.0);
            }

            System.out.println("[UserService] Đang lưu user: " + username
                    + " | role: " + newUser.getRole()
                    + " | id: " + id);

            // 4. Lưu vào DB
            boolean saved = saveDAO.saveUser(newUser);
            if (saved) {
                System.out.println("[UserService] REGISTER SUCCESS: " + username);
                return RegisterResult.SUCCESS;
            } else {
                System.out.println("[UserService] REGISTER FAIL: saveUser trả về false cho " + username);
                return RegisterResult.ERROR;
            }

        } catch (Exception e) {
            System.err.println("[UserService] REGISTER EXCEPTION: " + e.getMessage());
            e.printStackTrace();
            return RegisterResult.ERROR;
        }
    }

    // ══════════════════════════════════════════
    // ĐĂNG NHẬP — bằng username
    // ══════════════════════════════════════════
    public User login(String username, String password) {
        if (username == null || username.isBlank()) return null;
        if (password == null || password.isBlank()) return null;

        User user = findDAO.findByUsername(username);

        if (user == null) {
            System.out.println("[UserService] LOGIN FAIL: không tìm thấy username: " + username);
            return null;
        }

        if (!user.checkPassword(password)) {
            System.out.println("[UserService] LOGIN FAIL: sai mật khẩu cho: " + username);
            return null;
        }

        System.out.println("[UserService] LOGIN SUCCESS: " + username + " | role: " + user.getRole());
        return user;
    }
}