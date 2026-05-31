package com.auction.shared.model.Entity.User;

import com.auction.shared.model.Entity.Entity;

/**
 * 💡 LỚP CHA TRỪU TƯỢNG: USER (NGƯỜI DÙNG HỆ THỐNG)
 * - Quản lý các thông tin tài khoản cốt lõi như Email, Password, FullName, Phone, Address.
 * - Cung cấp cơ chế xác thực mật khẩu an toàn, chống lỗi không đồng bộ dữ liệu với Database.
 */
public abstract class User extends Entity {

    private String email;
    private String password;
    
    // ══════════════════════════════════════════
    // THUỘC TÍNH THÔNG TIN CÁ NHÂN (PROFILE)
    // ══════════════════════════════════════════
    private String fullName = "";
    private String phone = "";
    private String address = "";

    /**
     * Hàm khởi tạo (Constructor) có tham số đầy đủ cho thực thể Người dùng.
     */
    public User(String id, String username, String email, String password) {
        super(id, username);
        this.email    = email;
        this.password = password;
    }

    /** Constructor rỗng bắt buộc phải có phục vụ cho việc giải mã JSON bằng thư viện Gson (Deserialization) */
    public User() {}

    // ── Getters ──────────────────────────────────────────────────────────
    public String getUsername() { return getName(); }
    public String getEmail()    { return email; }
    public String getPassword() { return password; }

    public String getFullName() { 
        return fullName != null ? fullName : ""; 
    }

    public String getPhone() { 
        return phone != null ? phone : ""; 
    }

    public String getAddress() { 
        return address != null ? address : ""; 
    }

    // ── Setters ──────────────────────────────────────────────────────────
    public void setEmail(String email)       { this.email    = email; }
    public void setPassword(String password) { this.password = password; }

    public void setFullName(String fullName) { 
        this.fullName = fullName; 
    }

    public void setPhone(String phone) { 
        this.phone = phone; 
    }

    public void setAddress(String address) { 
        this.address = address; 
    }

    /**
     * 🔐 HÀM XÁC THỰC MẬT KHẨU (TẦNG SERVICE / CONTROLLER GỌI)
     * * 🔥 SỬA LỖI CHÍ MẠNG: Đăng ký thành công nhưng đăng nhập lại báo sai mật khẩu.
     * * Nguyên nhân: Do cột `password` trong MySQL thường định nghĩa kiểu CHAR(X) - kiểu độ dài cố định.
     * Khi lưu chuỗi ngắn (vd: "123456"), MySQL tự động bù thêm các dấu cách (khoảng trắng) phía sau cho đủ độ dài bảng.
     * Khi đọc lên Java, chuỗi mật khẩu từ DB sẽ bị dính hàng loạt khoảng trắng ("123456     "), khiến phép toán 
     * .equals() thông thường trả về FALSE.
     * * Giải pháp: Sử dụng hàm .trim() cho CẢ HAI chuỗi trước khi so sánh nhằm cắt bỏ triệt để khoảng trắng thừa vô hình.
     */
    public boolean checkPassword(String raw) {
        // Kiểm tra điều kiện an toàn phòng trường hợp một trong hai chuỗi bị Null (gây lỗi NullPointerException)
        if (this.password == null || raw == null) {
            return false;
        }
        // Tiến hành cắt khoảng trắng đầu cuối (.trim()) và so sánh chính xác giá trị thực tế
        return this.password.trim().equals(raw.trim());
    }

    // ── Abstract methods (Các lớp con kế thừa bắt buộc phải override bổ sung logic) ─────────────────────────
    /** Hiển thị thông tin vai trò đặc trưng của lớp con ra màn hình console (Tính đa hình - Polymorphism). */
    public abstract void displayRole();

    /** Trả về định danh vai trò dưới dạng chuỗi chuẩn: "BIDDER" / "SELLER" / "ADMIN". */
    public abstract String getRole();
}