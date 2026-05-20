package com.auction.server.service;

<<<<<<< HEAD
import com.auction.server.dao.UserDAO;
import com.auction.model.Entity.User.User;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UserService {

    private final UserDAO userDAO = new UserDAO();

    // ── LOGIN ──────────────────────────────────────────
    /**
     * @return User nếu đúng, null nếu sai
     */
    public User login(String email, String password) {
        User user = userDAO.findByEmail(email);
        if (user == null) return null;

        // So sánh password hash
        String hashed = hashPassword(password);
        if (!hashed.equals(user.getPasswordHash())) return null;

        return user;
    }

    // ── REGISTER ───────────────────────────────────────
    public enum RegisterResult { SUCCESS, EMAIL_EXISTS, USERNAME_EXISTS, ERROR }

    public RegisterResult register(String username, String email,
                                   String password, String fullName,
                                   String phone, String address,
                                   String role) {
        // Kiểm tra trùng
        if (userDAO.existsByEmail(email))
            return RegisterResult.EMAIL_EXISTS;
        if (userDAO.existsByUsername(username))
            return RegisterResult.USERNAME_EXISTS;

        // Hash password trước khi lưu — KHÔNG BAO GIỜ lưu plaintext
        String hashed = hashPassword(password);
        boolean ok = userDAO.save(username, email, hashed,
                fullName, phone, address, role);
        return ok ? RegisterResult.SUCCESS : RegisterResult.ERROR;
    }

    // ── Hash SHA-256 ───────────────────────────────────
    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 không khả dụng", e);
        }
    }
=======
import java.util.UUID;

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
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a
}