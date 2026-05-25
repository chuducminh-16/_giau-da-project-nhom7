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
        // Map đúng tất cả cột trong bảng users:
        // id, username, email, password, balance, role, rating, admin_level
        String sql = "INSERT INTO users (id, username, email, password, balance, role, rating, admin_level) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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

            pstmt.setString(1, user.getId());
            pstmt.setString(2, user.getUsername());
            pstmt.setString(3, user.getEmail());
            pstmt.setString(4, user.getPassword());
            pstmt.setDouble(5, balance);
            pstmt.setString(6, user.getRole());
            pstmt.setDouble(7, rating);
            pstmt.setInt   (8, adminLevel);

            int rows = pstmt.executeUpdate();
            System.out.println("[UserSaveDAO] INSERT rows affected: " + rows
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