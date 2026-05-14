package com.auction.model.Entity.Auction_Bid;

import com.auction.model.Entity.User.Bidder;

public class Bid {
    private Bidder bidder; //người đặt giá
    private double amount;  //số tiền đặt giá
    private long timestamp;  //thời gian đặt giá (dùng để xác định ai đặt giá trước nếu có cùng số tiền)

    public Bid(Bidder bidder, double amount) {
        this.bidder = bidder;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters
    public Bidder getBidder() { return bidder; }
    public double getAmount() { return amount; }
    public long getTimestamp() { return timestamp; }
}