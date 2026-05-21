package com.auction.shared.model.Entity.Item;

import java.time.LocalDateTime;

public class Electronics extends Item {

    private int warrantyPeriod; // số tháng bảo hành

    public Electronics(String id, String name, double startingPrice,
                       LocalDateTime endTime, String sellerId,
                       int warrantyPeriod) {
        super(id, name, startingPrice, endTime, sellerId);
        this.warrantyPeriod = warrantyPeriod;
        setDescription("Bảo hành: " + warrantyPeriod + " tháng");
    }

    /** Overload nhận String endTime. */
    public Electronics(String id, String name, double startingPrice,
                       String endTime, String sellerId,
                       int warrantyPeriod) {
        super(id, name, startingPrice,
                endTime != null ? LocalDateTime.parse(endTime) : null,
                sellerId);
        this.warrantyPeriod = warrantyPeriod;
        setDescription("Bảo hành: " + warrantyPeriod + " tháng");
    }

    // Constructor rỗng cho Gson
    public Electronics() { super(); }

    public int getWarrantyPeriod() { return warrantyPeriod; }
    public void setWarrantyPeriod(int warrantyPeriod) {
        this.warrantyPeriod = warrantyPeriod;
        setDescription("Bảo hành: " + warrantyPeriod + " tháng");
    }

    @Override
    public String getType() { return "ELECTRONICS"; }

    @Override
    public String showDetails() {
        return "[Electronics] " + getName() + " | Bảo hành: " + warrantyPeriod + " tháng";
    }
}
