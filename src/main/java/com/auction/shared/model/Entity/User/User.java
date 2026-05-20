package com.auction.shared.model.Entity.User;
 
import com.auction.shared.model.Entity.Entity;
 
public abstract class User extends Entity {
    private String email;
    private String password;
 
    public User(String id, String username, String email, String password) {
        super(id, username);
        this.email    = email;
        this.password = password;
    }
 
    public String getUsername() { return getName(); }
    public String getEmail()    { return email; }
 
    // Dùng cho tầng DAO khi INSERT/UPDATE vào database
    public String getPassword() { return password; }
 
    // Xác thực password (dùng ở tầng Service/Controller, không cần lộ raw password)
    public boolean checkPassword(String raw) { return this.password.equals(raw); }
 
    // Setter cho email
    public void setEmail(String email) { this.email = email; }
 
    // Abstract methods - các subclass phải override
    public abstract void displayRole();
    public abstract String getRole();

    protected abstract boolean save(String username, String email2, String hashed, String fullName, String phone,
            String address, String role);
}
 