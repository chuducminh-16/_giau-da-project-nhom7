package com.auction.model.Entity.Item;
 
import com.auction.model.Entity.Entity;
 
public abstract class Item extends Entity {
    private double startingPrice;  // giá khởi điểm
    private double currentPrice;   // giá hiện tại (thay đổi khi có bid)
    private String endTime;        // thời gian kết thúc
    private String sellerId;       // ID người bán
    private String status;         // OPEN / RUNNING / FINISHED / PAID / CANCELED
 
    public Item(String id, String name, double startingPrice, String endTime, String sellerId) {
        super(id, name);
        this.startingPrice = startingPrice;
        this.currentPrice  = startingPrice;  // ban đầu bằng giá khởi điểm
        this.endTime       = endTime;
        this.sellerId      = sellerId;
        this.status        = "OPEN";
    }
 
    // ---- Abstract methods ----
    public abstract String getType();
    public abstract String showDetails();
 
    // ---- Getters ----
    public double getStartingPrice() { return startingPrice; }
    public double getCurrentPrice()  { return currentPrice; }
    public String getEndTime()       { return endTime; }
    public String getSellerId()      { return sellerId; }
    public String getStatus()        { return status; }
 
    // ---- Setters ----
    public void setCurrentPrice(double currentPrice) {
        if (currentPrice >= this.startingPrice) {
            this.currentPrice = currentPrice;
        }
    }
 
    public void setStatus(String status) {
        this.status = status;
    }
 
    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }
}