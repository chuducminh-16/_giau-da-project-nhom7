package com.auction.shared.model.Entity.User;

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
    @Override
    protected boolean save(String username, String email2, String hashed, String fullName, String phone, String address,
            String role) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'save'");
    }
}

