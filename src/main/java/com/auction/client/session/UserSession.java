package com.auction.client.session;

/**
 * Singleton lưu trạng thái đăng nhập của user hiện tại.
 * Tất cả Controller dùng class này để biết ai đang đăng nhập.
 *
 * Cách dùng trong Controller:
 *   UserSession session = UserSession.getInstance();
 *   String role = session.getRole(); // "BIDDER", "SELLER", "ADMIN"
 *   boolean loggedIn = session.isLoggedIn();
 */
public class UserSession {

    // ── Singleton (Holder pattern — thread-safe) ────────
    private static class Holder {
        private static final UserSession INSTANCE = new UserSession();
    }

    public static UserSession getInstance() {
        return Holder.INSTANCE;
    }

    private UserSession() {}

    // ── Fields ──────────────────────────────────────────
    private String  userId;
    private String  username;
    private String  email;
    private String  role;     // "BIDDER" | "SELLER" | "ADMIN"
    private boolean loggedIn = false;

    // ── Đăng nhập thành công → lưu thông tin ───────────
    public void login(String userId, String username,
                      String email, String role) {
        this.userId   = userId;
        this.username = username;
        this.email    = email;
        this.role     = role;
        this.loggedIn = true;
        System.out.println("[UserSession] Đăng nhập: "
                + username + " (" + role + ")");
    }

    // ── Đăng xuất → xóa toàn bộ thông tin ─────────────
    public void logout() {
        this.userId   = null;
        this.username = null;
        this.email    = null;
        this.role     = null;
        this.loggedIn = false;
        System.out.println("[UserSession] Đã đăng xuất.");
    }

    // ── Getters ─────────────────────────────────────────
    public boolean isLoggedIn() { return loggedIn; }
    public String  getUserId()  { return userId;   }
    public String  getUsername(){ return username; }
    public String  getEmail()   { return email;    }
    public String  getRole()    { return role;     }

    // ── Tiện ích kiểm tra quyền ─────────────────────────
    public boolean isBidder() { return "BIDDER".equals(role); }
    public boolean isSeller() { return "SELLER".equals(role); }
    public boolean isAdmin()  { return "ADMIN".equals(role);  }

    @Override
    public String toString() {
        return "UserSession{userId='" + userId
                + "', username='" + username
                + "', role='" + role + "'}";
    }
}