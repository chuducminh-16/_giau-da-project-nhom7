package com.auction.model.Entity.User;

import com.auction.model.Entity.Entity;

public abstract class User extends Entity {
    private String email;
    private String password;

    public User(String id, String username, String email, String password) {
        super(id, username);
        this.email = email;
        this.password = password;
    }

    public String getUsername() {
        return getName();
    }

    public String getEmail() { return email; }       // ← thêm
    public String getPassword() { return password; } // ← thêm

    public void setUsername(String username) {
        this.name = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEmailMatch(String inputEmail) {
        return this.email.equalsIgnoreCase(inputEmail);
    }

    public boolean authenticate(String inputPassword) {
        return this.password.equals(inputPassword);
    }

    public abstract void displayRole();
    // Thêm vào User.java
    public abstract String getRole(); // ← abstract, bắt class con tự trả về
}