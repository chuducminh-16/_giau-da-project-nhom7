package com.auction.model;

import java.io.Serializable;

public abstract class User implements Serializable {
    private String id;
    private String username;
    private String password;
    private String role; // "BIDDER", "SELLER", "ADMIN"

    public User(String id, String username, String password, String role) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    // Encapsulation: Chỉ dùng Getter, không cho sửa username bừa bãi
    public String getUsername() { return username; }
    public String getRole() { return role; }
    
    // Phương thức trừu tượng để các lớp con tự định nghĩa quyền hạn
    public abstract void displayPermissions();
}