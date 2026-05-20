package com.auction.client.session;

/**
 * Singleton — lưu thông tin user đang đăng nhập.
 * Mọi Controller đều có thể gọi UserSession.getInstance().getCurrentUser()
 */
public class UserSession {

    private static UserSession instance;

    public static UserSession getInstance() {
        if (instance == null) instance = new UserSession();
        return instance;
    }

    private UserSession() {}

    // ── Dữ liệu user hiện tại ──────────────────────────
    private String userId;
    private String username;
    private String email;
    private String role;     // "BIDDER" | "SELLER" | "ADMIN"
    private String token;    // nếu server trả về token

    // ── Gán sau khi login thành công ──────────────────
    public void login(String userId, String username,
                      String email, String role) {
        this.userId   = userId;
        this.username = username;
        this.email    = email;
        this.role     = role;
    }

    // ── Xoá khi logout ────────────────────────────────
    public void logout() {
        userId = username = email = role = token = null;
    }

    // ── Kiểm tra ──────────────────────────────────────
    public boolean isLoggedIn() { return userId != null; }
    public boolean isSeller()   { return "SELLER".equals(role); }
    public boolean isBidder()   { return "BIDDER".equals(role); }

    // ── Getters ───────────────────────────────────────
    public String getUserId()   { return userId; }
    public String getUsername() { return username; }
    public String getEmail()    { return email; }
    public String getRole()     { return role; }
}
