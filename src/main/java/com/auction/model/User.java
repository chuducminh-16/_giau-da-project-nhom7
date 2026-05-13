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


    public String getId()       { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }  // ← FIX lỗi getPassword()
    public String getRole()     { return role; }

    public abstract void displayPermissions();
}