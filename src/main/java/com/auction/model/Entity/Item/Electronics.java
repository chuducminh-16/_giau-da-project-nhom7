package com.auction.model.Entity.Item;

public class Electronics extends Item {
    private int warrantyPeriod;

    public Electronics(String id, String name, double startingPrice, String endTime, String sellerId, int warrantyPeriod) {
        super(id, name, startingPrice, endTime, sellerId);
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