package com.auction.model.Entity.Item;

import java.time.LocalDateTime;

public class Vehicle extends Item {

    private int mileage; // số km đã đi

    public Vehicle(String id, String name, double startingPrice,
                   LocalDateTime endTime, String sellerId,
                   int mileage) {
        super(id, name, startingPrice, endTime, sellerId);
        this.mileage = mileage;
    }

    /** Overload nhận String endTime — tự parse để tương thích code cũ. */
    public Vehicle(String id, String name, double startingPrice,
                   String endTime, String sellerId,
                   int mileage) {
        super(id, name, startingPrice,
                endTime != null ? LocalDateTime.parse(endTime) : null,
                sellerId);
        this.mileage = mileage;
    }

    public int getMileage() { return mileage; }

    @Override
    public String getType() { return "VEHICLE"; }

    @Override
    public String showDetails() {
        return "[Vehicle] " + getName() + " | Số km: " + mileage + " km";
    }
}