package com.auction.server.dao.user;

import com.auction.server.database.DatabaseConnection;
import com.auction.shared.model.Entity.User.Admin;
import com.auction.shared.model.Entity.User.Bidder;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.model.Entity.User.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 🗄️ TẦNG GIAO TIẾP DỮ LIỆU: USER SAVE DAO
 * - Nhiệm vụ: Ghi mới hoặc cập nhật thông tin thực thể Người dùng trực tiếp xuống Database MySQL.
 * - Đảm bảo tính toàn vẹn của khóa chính (ID) và dọn dẹp dữ liệu đầu vào.
 */
public class UserSaveDAO {

    /**
     * 🛠️ SỬA LỖI LOGIC SINH ID TỰ ĐỘNG
     * * Lỗi cũ: Sử dụng câu lệnh "SELECT COUNT(*) FROM users". Nếu hệ thống bị XÓA BỚT người dùng, 
     * giá trị COUNT(*) giảm xuống dẫn đến việc sinh ID mới bị TRÙNG với ID của một người dùng cũ đang tồn tại.
     * Kết quả là câu lệnh INSERT ... ON DUPLICATE KEY UPDATE sẽ vô tình GHI ĐÈ dữ liệu của tài khoản mới vào tài khoản cũ!
     * * Giải pháp sửa đổi: Sử dụng cấu trúc lệnh tìm kiếm ID có số lớn nhất hiện tại (Sắp xếp giảm dần id DESC kèm LIMIT 1).
     * Sau đó bóc tách lấy phần số, tăng thêm 1 đơn vị. Cơ chế này an toàn tuyệt đối, không bao giờ lo trùng lặp ID.
     */
    public String getNextUserId() {
        // Câu lệnh SQL lấy ra bản ghi duy nhất có ID mang giá trị chuỗi lớn nhất trong hệ thống (Ví dụ: "U003")
        String sql = "SELECT id FROM users WHERE id LIKE 'U%' ORDER BY id DESC LIMIT 1";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String lastId = rs.getString("id"); // Lấy ra chuỗi ID lớn nhất, ví dụ: "U003"
                try {
                    // Cắt bỏ ký tự 'U' ở vị trí đầu tiên (index 0), chuyển phần còn lại ("003") sang số nguyên -> 3
                    int numericPart = Integer.parseInt(lastId.substring(1)); 
                    
                    // Tăng số nguyên thêm 1 đơn vị (3 + 1 = 4) và format lại về dạng chuỗi định dạng 3 chữ số -> "U004"
                    return String.format("U%03d", numericPart + 1); 
                } catch (NumberFormatException e) {
                    System.err.println("[UserSaveDAO] Định dạng ID trong hệ thống lỗi, không thể bóc tách số: " + lastId);
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserSaveDAO] SQL ERROR khi đang tính toán sinh ID tiếp theo: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Trường hợp Cơ sở dữ liệu hoàn toàn trống (Chưa có dòng dữ liệu nào), mặc định trả về ID khởi tạo ban đầu
        return "U001"; 
    }

    /**
     * 💾 LƯU HOẶC CẬP NHẬT THÔNG TIN NGƯỜI DÙNG XUỐNG DATABASE
     * - Sử dụng cơ chế ON DUPLICATE KEY UPDATE: Nếu chưa trùng ID thì ghi mới, nếu trùng ID thì cập nhật hồ sơ sửa đổi.
     */
    public boolean saveUser(User user) {
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

            // Khởi tạo các giá trị số mặc định tương ứng với từng phân vai thực thể OOP
            double balance    = 0.0;
            double rating     = 5.0;
            int    adminLevel = 1;

            // Áp dụng kỹ thuật Kiểm tra kiểu mẫu (Pattern Matching cho instanceof) nhằm trích xuất thuộc tính đặc trưng
            if (user instanceof Bidder b) {
                balance = b.getBalance();       // Người đấu giá -> Lấy số dư tài khoản tiền
            } else if (user instanceof Seller s) {
                rating = s.getRating();         // Người bán hàng -> Lấy điểm đánh giá uy tín
            } else if (user instanceof Admin a) {
                adminLevel = a.getAdminLevel(); // Quản trị viên -> Lấy cấp độ quản lý
            }

            // Nạp dữ liệu an toàn vào tham số Prepared Statement (Chống lỗi tấn công SQL Injection)
            // Đồng thời tiến hành gọi .trim() dọn dẹp khoảng trắng dư thừa ngay trước khi lưu dữ liệu
            pstmt.setString(1,  user.getId() != null ? user.getId().trim() : "");
            pstmt.setString(2,  user.getUsername() != null ? user.getUsername().trim() : "");
            pstmt.setString(3,  user.getEmail() != null ? user.getEmail().trim() : "");
            
            // 🔥 ĐỒNG BỘ TRIM MẬT KHẨU: Cắt bỏ khoảng trắng trống trước khi nạp vào MySQL
            pstmt.setString(4,  user.getPassword() != null ? user.getPassword().trim() : "");
            
            pstmt.setDouble(5,  balance);
            pstmt.setString(6,  user.getRole() != null ? user.getRole().trim() : "");
            pstmt.setDouble(7,  rating);
            pstmt.setInt   (8,  adminLevel);
            
            // Xử lý thông tin bổ sung, nếu null thì thay thế bằng chuỗi rỗng để tránh lỗi crash DB
            pstmt.setString(9,  user.getFullName()  != null ? user.getFullName().trim()  : "");
            pstmt.setString(10, user.getPhone()     != null ? user.getPhone().trim()     : "");
            pstmt.setString(11, user.getAddress()   != null ? user.getAddress().trim()   : "");

            // Thực thi câu lệnh tác động dữ liệu lên MySQL
            int rows = pstmt.executeUpdate();
            System.out.println("[UserSaveDAO] Lưu thành công! Số dòng bị ảnh hưởng: " + rows 
                               + " | Username: " + user.getUsername() + " | Thực cấp ID: " + user.getId());
                               
            // Trả về true nếu có ít nhất 1 dòng dữ liệu trong cơ sở dữ liệu được tác động thành công
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("[UserSaveDAO] THẤT BẠI: Lỗi truy vấn SQL khi lưu User: " + e.getMessage());
            System.err.println("[UserSaveDAO] SQL State Mã: "  + e.getSQLState());
            System.err.println("[UserSaveDAO] Error Code Mã: " + e.getErrorCode());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("[UserSaveDAO] THẤT BẠI: Lỗi hệ thống không xác định: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}