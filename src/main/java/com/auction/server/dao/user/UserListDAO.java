package com.auction.server.dao.user;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.User.Admin;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

public class UserListDAO {

    // Lấy toàn bộ danh sách user
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }

    // Lấy danh sách user theo role
    public List<User> findByRole(String role) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, role);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                users.add(mapRow(rs));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }

    /**
     * ✨ SỬA ĐỔI: Map ResultSet đa hình theo đúng subclass User dựa vào cột role trong DB,
     * đồng thời nạp đầy đủ thông tin cá nhân (full_name, phone, address).
     */
    private User mapRow(ResultSet rs) throws Exception {
        String id       = rs.getString("id");
        String username = rs.getString("username");
        String email    = rs.getString("email");
        String password = rs.getString("password");
        String role     = rs.getString("role");
        double balance  = rs.getDouble("balance");
        double rating   = rs.getDouble("rating");
        int adminLevel  = rs.getInt("admin_level");

        // Đọc thêm dữ liệu từ 3 cột thông tin cá nhân mới
        String fullName = rs.getString("full_name");
        String phone    = rs.getString("phone");
        String address  = rs.getString("address");

        // Khởi tạo đúng Class con (Subclass) theo cấu trúc đa hình hệ thống
        User user = switch (role != null ? role.toUpperCase() : "BIDDER") {
            case "SELLER" -> new Seller(id, username, email, password, rating);
            case "ADMIN"  -> new Admin(id, username, email, password, adminLevel);
            default       -> new Bidder(id, username, email, password, balance);
        };

        // Thiết lập đầy đủ dữ liệu Profile cá nhân vào đối tượng User trước khi đưa vào danh sách công việc
        user.setFullName(fullName != null ? fullName : "");
        user.setPhone(phone != null ? phone : "");
        user.setAddress(address != null ? address : "");

        return user;
    }
}