package com.auction.model;

public class Bidder extends User {
    private double balance; // Số dư tài khoản

    public Bidder(String id, String username, String password, double balance) {
        super(id, username, password, "BIDDER");
        this.balance = balance;
    }

    @Override
    public void displayPermissions() {
        System.out.println("Quyền: Xem sản phẩm và Đặt giá.");
    }

    public double getBalance() { return balance; }
}