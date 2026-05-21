package com.auction.shared.model.Entity.Item;

import java.time.LocalDateTime;

public class Vehicle extends Item {

    private int mileage; // số km đã đi

    public Vehicle(String id, String name, double startingPrice,
                   LocalDateTime endTime, String sellerId,
                   int mileage) {
        super(id, name, startingPrice, endTime, sellerId);
        this.mileage = mileage;
        setDescription("Số km: " + mileage + " km");
    }

    /** Overload nhận String endTime. */
    public Vehicle(String id, String name, double startingPrice,
                   String endTime, String sellerId,
                   int mileage) {
        super(id, name, startingPrice,
                endTime != null ? LocalDateTime.parse(endTime) : null,
                sellerId);
        this.mileage = mileage;
        setDescription("Số km: " + mileage + " km");
    }

    // Constructor rỗng cho Gson
    public Vehicle() { super(); }

    public int getMileage() { return mileage; }
    public void setMileage(int mileage) {
        this.mileage = mileage;
        setDescription("Số km: " + mileage + " km");
    }

    @Override
    public String getType() { return "VEHICLE"; }

    @Override
    public String showDetails() {
        return "[Vehicle] " + getName() + " | Số km: " + mileage + " km";
    }
}
