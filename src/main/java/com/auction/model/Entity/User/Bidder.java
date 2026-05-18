package com.auction.model.Entity.User;

public class Bidder extends User {
    private double balance; //số dư
    public Bidder(String id, String username, String email, String password, double balance) {
        super(id, username, email, password);
        this.balance = balance;
    }
    // xem số dư
    public double getBalance() {
        return balance;
    }
    // nạp tiền hoặc trừ tiền
    public void setBalance(double balance) {
        if(balance >= 0){
            this.balance = balance;
        }
    }
    @Override
    public void displayRole() {
        System.out.println("[Bidder] Username: " + getUsername() + " | Wallet: $" + balance);
        // Hiển thị thông tin người dùng với vai trò Bidder
    }
    @Override
    public String getRole() { return "BIDDER"; }
}

