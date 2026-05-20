package com.auction.shared.model.Entity.User;

public class Admin extends User {
    private int adminLevel; // Cấp độ quản trị viên
    public Admin(String id, String username, String email, String password, int adminLevel) {
        super(id, username, email, password);
        this.adminLevel = adminLevel;
    }
    public int getAdminLevel() { // Xem cấp độ quản trị viên
        return adminLevel;
    }
    // admin có thể cập nhật cấp độ quản trị viên
    public void setAdminLevel(int adminLevel) {
        this.adminLevel = adminLevel;
    }
    @Override
    public void displayRole() {
        System.out.println("[Admin] Username: " + getUsername() + " | Access Level: " + adminLevel);
    }
    @Override
    public String getRole() { return "ADMIN"; }
    @Override
    protected boolean save(String username, String email2, String hashed, String fullName, String phone, String address,
            String role) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'save'");
    }
}

