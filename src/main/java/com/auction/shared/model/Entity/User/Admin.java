package com.auction.shared.model.Entity.User;

/**
 * Admin — quan tri vien he thong.
 *
 * FIX: xoa override save() nem UnsupportedOperationException.
 */
public class Admin extends User {

    private int adminLevel;

    public Admin(String id, String username, String email, String password, int adminLevel) {
        super(id, username, email, password);
        this.adminLevel = adminLevel;
    }

    /** Constructor rong cho Gson */
    public Admin() {}

    public int getAdminLevel() { return adminLevel; }

    public void setAdminLevel(int adminLevel) {
        if (adminLevel >= 0) this.adminLevel = adminLevel;
    }

    @Override
    public void displayRole() {
        System.out.println("[Admin] Username: " + getUsername()
                + " | Level: " + adminLevel);
    }

    @Override
    public String getRole() { return "ADMIN"; }
}
