package com.auction.model.Entity.Item;

import java.time.LocalDateTime;

public class Electronics extends Item {

    private int warrantyPeriod; // số tháng bảo hành

    public Electronics(String id, String name, double startingPrice,
                       LocalDateTime endTime, String sellerId,
                       int warrantyPeriod) {
        super(id, name, startingPrice, endTime, sellerId);
        this.warrantyPeriod = warrantyPeriod;
    }

    /** Overload nhận String endTime — tự parse để tương thích code cũ. */
    public Electronics(String id, String name, double startingPrice,
                       String endTime, String sellerId,
                       int warrantyPeriod) {
        super(id, name, startingPrice,
                endTime != null ? LocalDateTime.parse(endTime) : null,
                sellerId);
        this.warrantyPeriod = warrantyPeriod;
    }

    public int getWarrantyPeriod() { return warrantyPeriod; }

    @Override
    public String getType() { return "ELECTRONICS"; }

    @Override
    public String showDetails() {
        return "[Electronics] " + getName() + " | Bảo hành: " + warrantyPeriod + " tháng";
    }
}