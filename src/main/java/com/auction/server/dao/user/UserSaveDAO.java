package com.auction.server.dao.user;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.User.Admin;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UserSaveDAO {

    public boolean saveUser(User user) {
        // ✨ CẢI TIẾN: Bổ sung 3 cột cá nhân (full_name, phone, address) vào câu lệnh dữ liệu.
        // Dùng ON DUPLICATE KEY UPDATE để tự động chuyển từ INSERT thành UPDATE nếu trùng ID/Username.
        String sql = "INSERT INTO users (id, username, email, password, balance, role, rating, admin_level, full_name, phone, address) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                   + "ON DUPLICATE KEY UPDATE "
                   + "email = VALUES(email), "
                   + "password = VALUES(password), "
                   + "full_name = VALUES(full_name), "
                   + "phone = VALUES(phone), "
                   + "address = VALUES(address)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Xác định balance, rating, admin_level theo role
            double balance    = 0.0;
            double rating     = 5.0;
            int    adminLevel = 1;

            if (user instanceof Bidder b) {
                balance = b.getBalance();   // thường 1000.0
            } else if (user instanceof Seller s) {
                rating  = s.getRating();    // thường 5.0
            } else if (user instanceof Admin a) {
                adminLevel = a.getAdminLevel();
            }

            // Gán các tham số từ 1 đến 8 (Dữ liệu cốt lõi hệ thống)
            pstmt.setString(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getEmail());
            pstmt.setString(4, user.getPassword());
            pstmt.setDouble(5, balance);
            pstmt.setString(6, user.getRole());
            pstmt.setDouble(7, rating);
            pstmt.setInt   (8, adminLevel);

            // ➕ Gán các tham số từ 9 đến 11 (Thông tin cá nhân mới bổ sung)
            pstmt.setString(9, user.getFullName() != null ? user.getFullName() : "");
            pstmt.setString(10, user.getPhone() != null ? user.getPhone() : "");
            pstmt.setString(11, user.getAddress() != null ? user.getAddress() : "");

            int rows = pstmt.executeUpdate();
            System.out.println("[UserSaveDAO] SQL EXECUTE rows affected: " + rows
                    + " | user: " + user.getUsername());
            return rows > 0;

        } catch (SQLException e) {
            // In chi tiết SQLException để dễ debug
            System.err.println("[UserSaveDAO] SQL ERROR: " + e.getMessage());
            System.err.println("[UserSaveDAO] SQL State: " + e.getSQLState());
            System.err.println("[UserSaveDAO] Error Code: " + e.getErrorCode());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("[UserSaveDAO] UNEXPECTED ERROR: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}