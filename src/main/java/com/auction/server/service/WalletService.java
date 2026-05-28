package com.auction.server.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.auction.server.database.DatabaseConnection;

/**
 * WalletService — xử lý nạp tiền và trừ tiền balance của Bidder.
 * File hoàn toàn mới, không can thiệp vào các service cũ.
 */
public class WalletService {

    /**
     * Nạp tiền vào tài khoản Bidder.
     * @return balance mới sau khi nạp, hoặc -1 nếu lỗi
     */
    public double topUp(String userId, double amount) {
        System.out.println("[WalletService] userId='" + userId + "' amount=" + amount);  // THÊM DÒNG NÀY

        String sql = "UPDATE users SET balance = balance + ? WHERE id = ? AND role = 'BIDDER'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, userId);
            int rows = ps.executeUpdate();
            System.out.println("[WalletService] rows updated=" + rows);  // THÊM DÒNG NÀY
        if (rows > 0) {
            return getBalance(userId);
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Trừ tiền khi Bidder thắng đấu giá.
     * @return true nếu trừ thành công (đủ tiền), false nếu không đủ hoặc lỗi
     */
    public boolean deductBalance(String userId, double amount) {
        if (amount <= 0) return false;

        // Kiểm tra số dư trước
        double current = getBalance(userId);
        if (current < amount) {
            System.out.printf("[WalletService] %s không đủ số dư (%.0f < %.0f)%n",
                    userId, current, amount);
            return false; // Không đủ tiền nhưng vẫn ghi nhận thắng (bài tập)
        }

        String sql = "UPDATE users SET balance = balance - ? WHERE id = ? AND balance >= ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, amount);
            ps.setString(2, userId);
            ps.setDouble(3, amount);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Lấy số dư hiện tại của user.
     */
    public double getBalance(String userId) {
        String sql = "SELECT balance FROM users WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
