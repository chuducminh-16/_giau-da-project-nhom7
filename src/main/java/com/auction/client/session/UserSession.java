package com.auction.client.session;

/**
 * Singleton — lưu thông tin user đang đăng nhập.
 * Mọi Controller đều có thể gọi UserSession.getInstance()
 */
public class UserSession {

    private static UserSession instance;

    public static synchronized UserSession getInstance() {
        if (instance == null) instance = new UserSession();
        return instance;
    }

    private UserSession() {}

    // ── Dữ liệu user hiện tại ──────────────────────────
    private String userId;
    private String username;
    private String fullName;  // Thêm mới trường Họ và tên
    private String email;
    private String phone;     // Thêm mới trường Số điện thoại
    private String address;   // Thêm mới trường Địa chỉ
    private String role;      // "BIDDER" | "SELLER" | "ADMIN"
    private String token;     // nếu server trả về token

    // ── Gán sau khi login thành công (Cập nhật nhận thêm fullName, phone, address) ──
    public void login(String userId, String username, String fullName, 
                      String email, String phone, String address, String role) {
        this.userId   = userId;
        this.username = username;
        this.fullName = fullName;
        this.email    = email;
        this.phone    = phone;
        this.address  = address;
        this.role     = role;
    }

    // ── Xoá khi logout ────────────────────────────────
    public void logout() {
        userId = username = fullName = email = phone = address = role = token = null;
    }

    // ── Kiểm tra ──────────────────────────────────────
    public boolean isLoggedIn() { return userId != null; }
    public boolean isSeller()   { return "SELLER".equals(role); }
    public boolean isBidder()   { return "BIDDER".equals(role); }

    // ── Getters & Setters ─────────────────────────────
    public String getUserId()   { return userId; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }  // Thêm mới Getter
    public String getEmail()    { return email; }
    public String getPhone()    { return phone; }     // Thêm mới Getter
    public String getAddress()  { return address; }   // Thêm mới Getter
    public String getRole()     { return role; }
    public String getToken()    { return token; }
    public void setToken(String token) { this.token = token; }
}