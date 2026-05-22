package com.auction.server.dao.user;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * UserSaveDAO — luu user moi vao DB.
 *
 * FIX so voi ban goc:
 *   - Ban goc: pstmt.setDouble(6, 0.0) — luon luu balance = 0, bo qua Bidder.balance
 *   - Ban fix: lay dung balance tu Bidder, rating tu Seller, admin_level tu Admin
 */
public class UserSaveDAO {

    public boolean saveUser(User user) {
        // Thu INSERT day du (bang co cot rating, admin_level)
        String sql = "INSERT INTO users (id, username, email, password, role, balance, rating, admin_level) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getEmail());
            ps.setString(4, user.getPassword());
            ps.setString(5, user.getRole());

            // FIX: lay dung balance tu Bidder (ban goc luon set 0.0)
            double balance = (user instanceof Bidder b) ? b.getBalance() : 0.0;
            ps.setDouble(6, balance);

            // rating: lay tu Seller neu co, con lai 5.0
            double rating  = (user instanceof Seller s) ? s.getRating() : 5.0;
            ps.setDouble(7, rating);

            // admin_level: 1 cho Admin, 0 cho cac role khac
            int adminLevel = "ADMIN".equals(user.getRole()) ? 1 : 0;
            ps.setInt(8, adminLevel);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            // Fallback: DB khong co cot rating / admin_level
            String sqlFallback = "INSERT INTO users (id, username, email, password, role, balance) "
                               + "VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlFallback)) {

                ps.setString(1, user.getId());
                ps.setString(2, user.getUsername());
                ps.setString(3, user.getEmail());
                ps.setString(4, user.getPassword());
                ps.setString(5, user.getRole());
                // FIX: van lay dung balance ngay ca trong fallback
                double balance = (user instanceof Bidder b) ? b.getBalance() : 0.0;
                ps.setDouble(6, balance);
                return ps.executeUpdate() > 0;

            } catch (Exception ex) {
                ex.printStackTrace();
                return false;
            }
        }
    }
}
