package com.auction.shared.model.Entity.User;

import com.auction.shared.model.Entity.Entity;

/**
 * Lop cha truu tuong cua tat ca user trong he thong.
 *
 * FIX: xoa method abstract save() vi phạm SRP.
 * - Logic luu DB thuoc ve DAO, khong thuoc Model.
 * - Truoc day cac subclass override save() nem UnsupportedOperationException,
 * gay loi khi giao vien hoi va khong giai thich duoc.
 */
public abstract class User extends Entity {

    private String email;
    private String password;
    
    // ══════════════════════════════════════════
    // ➕ BỔ SUNG: CÁC THUỘC TÍNH THÔNG TIN CÁ NHÂN (Fix lỗi biên dịch hệ thống)
    // ══════════════════════════════════════════
    private String fullName = "";
    private String phone = "";
    private String address = "";

    public User(String id, String username, String email, String password) {
        super(id, username);
        this.email    = email;
        this.password = password;
    }

    /** Constructor rong cho Gson deserialization */
    public User() {}

    // ── Getters ──────────────────────────────────────────────────────────
    public String getUsername() { return getName(); }
    public String getEmail()    { return email; }
    public String getPassword() { return password; }

    // ══════════════════════════════════════════
    // ➕ BỔ SUNG: CÁC HÀM GETTER MỚI CHO PROFILE
    // ══════════════════════════════════════════
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

    // ══════════════════════════════════════════
    // ➕ BỔ SUNG: CÁC HÀM SETTER MỚI CHO PROFILE
    // ══════════════════════════════════════════
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
     * Xac thuc mat khau tai tang Service.
     */
    public boolean checkPassword(String raw) {
        return this.password != null && this.password.equals(raw);
    }

    // ── Abstract methods (subclass phai override) ─────────────────────────
    /** Hien thi thong tin vai tro ra console (Polymorphism). */
    public abstract void displayRole();

    /** Tra ve role dang chuoi: "BIDDER" / "SELLER" / "ADMIN". */
    public abstract String getRole();
}