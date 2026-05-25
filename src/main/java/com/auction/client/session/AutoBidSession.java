package com.auction.client.session;

public class AutoBidSession {

    private static AutoBidSession instance;

    public static AutoBidSession getInstance() {
        if (instance == null) instance = new AutoBidSession();
        return instance;
    }

    private AutoBidSession() {}

    private boolean active       = false;
    private double  maxPrice     = 0;
    private String  productId    = null;
    private String  userId       = null;
    private double  lastKnownBid = 0;
    private double  bidIncrement = 0; // FIX Bug 1: thêm field này

    // FIX Bug 1: thêm tham số bidIncrement
    public void activate(String userId, String productId,
                         double maxPrice, double currentBid, double bidIncrement) {
        this.active       = true;
        this.maxPrice     = maxPrice;
        this.productId    = productId;
        this.userId       = userId;
        this.lastKnownBid = currentBid;
        this.bidIncrement = bidIncrement;
    }

    public void updateLastKnownBid(double bid) {
        if (active) this.lastKnownBid = bid;
    }

    public void clear() {
        this.active       = false;
        this.maxPrice     = 0;
        this.productId    = null;
        this.userId       = null;
        this.lastKnownBid = 0;
        this.bidIncrement = 0;
    }

    public boolean isActive()        { return active; }
    public double  getMaxPrice()     { return maxPrice; }
    public String  getProductId()    { return productId; }
    public String  getUserId()       { return userId; }
    public double  getLastKnownBid() { return lastKnownBid; }
    public double  getBidIncrement() { return bidIncrement; } // FIX Bug 1

    public boolean isActiveForProduct(String uid, String pid) {
        return active
            && uid != null && uid.equals(this.userId)
            && pid != null && pid.equals(this.productId);
    }
}