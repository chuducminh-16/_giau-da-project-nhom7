package com.auction.server.service;

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
}