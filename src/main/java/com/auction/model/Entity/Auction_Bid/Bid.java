package com.auction.model.Entity.Auction_Bid;
 
import com.auction.model.Entity.User.Bidder;
 
public class Bid {
    private Bidder bidder;   // người đặt giá
    private double amount;   // số tiền đặt giá  ← field bị thiếu trong bản gốc
    private long   timestamp; // thời điểm đặt giá (ms)
 
    public Bid(Bidder bidder, double amount) {
        this.bidder    = bidder;
        this.amount    = amount;
        this.timestamp = System.currentTimeMillis();
    }
 
    // Getters
    public Bidder getBidder()   { return bidder; }
    public double getAmount()   { return amount; }
    public long   getTimestamp(){ return timestamp; }
 
    @Override
    public String toString() {
        return "Bid{bidder=" + bidder.getUsername()
                + ", amount=" + amount
                + ", timestamp=" + timestamp + "}";
    }
}