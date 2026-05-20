package com.auction.server.service;

<<<<<<< HEAD
import com.auction.server.dao.UserDAO;
import com.auction.shared.model.Entity.User.User;
=======
import java.util.UUID;
>>>>>>> 047e37a682ea24854e4fa3367031b48d42a35874

import com.auction.server.dao.user.UserFindDAO;
import com.auction.server.dao.user.UserSaveDAO;
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
        // 1. Validate
        if (username == null || username.isBlank()) return RegisterResult.ERROR;
        if (email == null || !email.contains("@"))  return RegisterResult.ERROR;
        if (password == null || password.length() < 6) return RegisterResult.ERROR;

        // 2. Kiểm tra username trùng
        if (findDAO.findByUsername(username) != null) {
            return RegisterResult.USERNAME_EXISTS;
        }

        // 3. Tạo user theo role
        String id = UUID.randomUUID().toString().substring(0, 8);
        User newUser;
        if ("SELLER".equalsIgnoreCase(role)) {
            newUser = new Seller(id, username, email, password, 5.0);
        } else {
            newUser = new Bidder(id, username, email, password, 1000.0);
        }

        // 4. Lưu DB
        boolean saved = saveDAO.saveUser(newUser);
        return saved ? RegisterResult.SUCCESS : RegisterResult.ERROR;
    }

    // ══════════════════════════════════════════
    // ĐĂNG NHẬP — bằng username
    // ══════════════════════════════════════════
    public User login(String username, String password) {
        if (username == null || username.isBlank()) return null;
        if (password == null || password.isBlank()) return null;

        User user = findDAO.findByUsername(username);

        if (user == null) {
            System.out.println("[UserService] Không tìm thấy username: " + username);
            return null;
        }

        if (!user.checkPassword(password)) {
            System.out.println("[UserService] Sai mật khẩu cho: " + username);
            return null;
        }

        System.out.println("[UserService] Đăng nhập thành công: " + username);
        return user;
    }
}