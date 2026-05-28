package com.auction.server.service;

import com.auction.server.dao.user.UserFindDAO;
import com.auction.server.dao.user.UserSaveDAO;
import com.auction.shared.model.Entity.User.Admin;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

/**
 * 🛠️ TẦNG DỊCH VỤ TÀI KHOẢN (USER SERVICE)
 * - Nhiệm vụ: Thực thi các nghiệp vụ logic cốt lõi (Validation, phân tách thực thể OOP).
 * - Kết nối trực tiếp với tầng dữ liệu (UserFindDAO, UserSaveDAO) để đọc/ghi xuống Database hoặc File hệ thống.
 */
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
    // ĐĂNG KÝ TÀI KHOẢN MỚI
    // ══════════════════════════════════════════
    public RegisterResult register(String username, String email, String password,
                                   String fullName, String phone, String address,
                                   String role) {
        try {
            // 1. Validate kiểm tra dữ liệu đầu vào cơ bản
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

            // 2. Kiểm tra xem tên đăng nhập (username) này đã có ai đăng ký chưa
            User existing = findDAO.findByUsername(username);
            if (existing != null) {
                System.out.println("[UserService] REGISTER FAIL: username đã tồn tại: " + username);
                return RegisterResult.USERNAME_EXISTS;
            }

            // 3. Sinh ID tuần tự thay vì UUID ngẫu nhiên → "U001", "U002"...
            String id = saveDAO.getNextUserId();

            // 4. Khởi tạo đối tượng đa hình (Polymorphism) theo Vai trò người dùng
            User newUser;
            String normalizedRole = (role != null) ? role.toUpperCase() : "BIDDER";

            switch (normalizedRole) {
                case "SELLER" -> newUser = new Seller(id, username, email, password, 5.0);
                case "ADMIN"  -> newUser = new Admin(id, username, email, password, 1);
                default       -> newUser = new Bidder(id, username, email, password, 1000.0);
            }

            // 5. Thiết lập đầy đủ thông tin cá nhân vào đối tượng trước khi lưu
            newUser.setFullName(fullName != null ? fullName.trim() : "");
            newUser.setPhone(phone != null ? phone.trim() : "");
            newUser.setAddress(address != null ? address.trim() : "");

            System.out.println("[UserService] Dang luu user: " + username
                    + " | role: " + newUser.getRole()
                    + " | id: " + id);

            // 6. Đẩy thực thể xuống tầng DAO để ghi lại vào Database
            boolean saved = saveDAO.saveUser(newUser);
            if (saved) {
                System.out.println("[UserService] REGISTER SUCCESS: " + username);
                return RegisterResult.SUCCESS;
            } else {
                System.out.println("[UserService] REGISTER FAIL: saveUser tra ve false cho " + username);
                return RegisterResult.ERROR;
            }

        } catch (Exception e) {
            System.err.println("[UserService] REGISTER EXCEPTION: " + e.getMessage());
            e.printStackTrace();
            return RegisterResult.ERROR;
        }
    }

    // ══════════════════════════════════════════
    // ĐĂNG NHẬP HỆ THỐNG
    // ══════════════════════════════════════════
    public User login(String username, String password) {
        if (username == null || username.isBlank()) return null;
        if (password == null || password.isBlank()) return null;

        // Tìm kiếm thực thể người dùng theo Username định danh
        User user = findDAO.findByUsername(username);

        if (user == null) {
            System.out.println("[UserService] LOGIN FAIL: khong tim thay username: " + username);
            return null;
        }

        // Kiểm tra khớp mật khẩu
        if (!user.checkPassword(password)) {
            System.out.println("[UserService] LOGIN FAIL: sai mat khau cho: " + username);
            return null;
        }

        System.out.println("[UserService] LOGIN SUCCESS: " + username + " | role: " + user.getRole());
        return user;
    }

    // ══════════════════════════════════════════
    // CẬP NHẬT HỒ SƠ TÀI KHOẢN (UPDATE PROFILE)
    // ══════════════════════════════════════════
    public boolean updateProfile(String username, String fullName, String email,
                                 String phone, String password, String address) {
        try {
            System.out.println("[UserService] Bat dau thuc thi update ho so cho: " + username);

            // Validate
            if (fullName == null || fullName.isBlank()) {
                System.out.println("[UserService] UPDATE FAIL: Ho ten khong duoc trong");
                return false;
            }
            if (email == null || !email.contains("@")) {
                System.out.println("[UserService] UPDATE FAIL: Email khong dung dinh dang");
                return false;
            }

            // Bước 1: Tìm kiếm tài khoản cần sửa trong Database
            User user = findDAO.findByUsername(username);
            if (user == null) {
                System.out.println("[UserService] UPDATE PROFILE FAIL: Khong tim thay username " + username);
                return false;
            }

            // Bước 2: Cập nhật dữ liệu
            user.setFullName(fullName.trim());
            user.setEmail(email.trim());
            user.setPhone(phone != null ? phone.trim() : "");
            user.setAddress(address != null ? address.trim() : "");

            // Chỉ đổi mật khẩu nếu người dùng nhập mới
            if (password != null && !password.isBlank() && password.length() >= 6) {
                user.setPassword(password);
                System.out.println("[UserService] Nguoi dung yeu cau thay doi Mat khau moi.");
            }

            // Bước 3: Ghi đè xuống DB
            boolean isUpdated = saveDAO.saveUser(user);

            if (isUpdated) {
                System.out.println("[UserService] UPDATE PROFILE SUCCESS: Da ghi du lieu moi cho " + username);
                return true;
            } else {
                System.out.println("[UserService] UPDATE PROFILE FAIL: Ghi du lieu that bai.");
                return false;
            }

        } catch (Exception e) {
            System.err.println("[UserService] UPDATE PROFILE EXCEPTION: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}