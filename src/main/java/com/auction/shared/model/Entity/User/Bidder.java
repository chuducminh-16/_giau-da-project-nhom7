package com.auction.shared.model.Entity.User;

/**
 * Bidder — nguoi tham gia dau gia.
 *
 * FIX: xoa override save() nem UnsupportedOperationException.
 *   Logic luu DB thuoc ve UserSaveDAO, khong phai Model.
 */
public class Bidder extends User {

    private double balance;

    public Bidder(String id, String username, String email, String password, double balance) {
        super(id, username, email, password);
        this.balance = balance;
    }

    /** Constructor rong cho Gson */
    public Bidder() {}

    public double getBalance() { return balance; }

    public void setBalance(double balance) {
        if (balance >= 0) this.balance = balance;
    }

    @Override
    public void displayRole() {
        System.out.println("[Bidder] Username: " + getUsername()
                + " | Wallet: " + String.format("%,.0f VND", balance));
    }

    @Override
    public String getRole() { return "BIDDER"; }
}
