package com.auction.server.service;

import java.util.UUID;

import com.auction.server.dao.user.UserFindDAO;
import com.auction.server.dao.user.UserSaveDAO;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

public class UserService {

    // UserService cần 2 DAO để làm việc
    private final UserFindDAO findDAO = new UserFindDAO();
    private final UserSaveDAO saveDAO = new UserSaveDAO();

    // ------------------------------------------------------------------ //
    //  ĐĂNG KÝ                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Đăng ký tài khoản Bidder mới.
     * Trả về true nếu thành công, false nếu thất bại.
     */
    public boolean registerBidder(String username, String email, String password) {

        // Bước 1: Validate input — kiểm tra dữ liệu đầu vào
        if (username == null || username.isBlank()) {
            System.out.println("Lỗi: Username không được để trống!");
            return false;
        }
        if (email == null || !email.contains("@")) {
            System.out.println("Lỗi: Email không hợp lệ!");
            return false;
        }
        if (password == null || password.length() < 6) {
            System.out.println("Lỗi: Mật khẩu phải từ 6 ký tự trở lên!");
            return false;
        }

        // Bước 2: Kiểm tra username đã tồn tại chưa
        // findDAO.findByUsername trả về null nếu chưa có → OK để đăng ký
        if (findDAO.findByUsername(username) != null) {
            System.out.println("Lỗi: Username '" + username + "' đã tồn tại!");
            return false;
        }

        // Bước 3: Tạo id tự động bằng UUID
        // UUID đảm bảo id luôn unique, không trùng nhau
        String id = "U-" + UUID.randomUUID().toString().substring(0, 8);

        // Bước 4: Tạo object Bidder rồi lưu vào DB
        Bidder newBidder = new Bidder(id, username, email, password, 1000.0);
        return saveDAO.saveUser(newBidder);
    }

    /**
     * Đăng ký tài khoản Seller mới.
     */
    public boolean registerSeller(String username, String email, String password) {

        // Validate giống Bidder
        if (username == null || username.isBlank()) {
            System.out.println("Lỗi: Username không được để trống!");
            return false;
        }
        if (email == null || !email.contains("@")) {
            System.out.println("Lỗi: Email không hợp lệ!");
            return false;
        }
        if (password == null || password.length() < 6) {
            System.out.println("Lỗi: Mật khẩu phải từ 6 ký tự trở lên!");
            return false;
        }
        if (findDAO.findByUsername(username) != null) {
            System.out.println("Lỗi: Username '" + username + "' đã tồn tại!");
            return false;
        }

        String id = "S-" + UUID.randomUUID().toString().substring(0, 8);
        Seller newSeller = new Seller(id, username, email, password, 5.0);
        return saveDAO.saveUser(newSeller);
    }

    // ------------------------------------------------------------------ //
    //  ĐĂNG NHẬP                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Đăng nhập — trả về object User nếu thành công, null nếu sai.
     *
     * Tại sao trả về User thay vì boolean?
     * → Vì sau khi login thành công, Controller cần biết user là ai
     *   (id, role, balance...) để hiển thị đúng màn hình.
     */
    public User login(String username, String password) {

        // Bước 1: Validate
        if (username == null || username.isBlank()) {
            System.out.println("Lỗi: Username không được để trống!");
            return null;
        }
        if (password == null || password.isBlank()) {
            System.out.println("Lỗi: Password không được để trống!");
            return null;
        }

        // Bước 2: Tìm user theo username
        User user = findDAO.findByUsername(username);

        // Bước 3: Kiểm tra password
        // checkPassword() so sánh password nhập vào với password trong DB
        if (user == null || !user.checkPassword(password)) {
            System.out.println("Lỗi: Username hoặc mật khẩu không đúng!");
            return null;
        }

        // Bước 4: Đăng nhập thành công → trả về user
        System.out.println("Đăng nhập thành công! Xin chào " + user.getUsername());
        return user;
    }
}