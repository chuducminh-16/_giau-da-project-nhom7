package com.auction.model.Entity.Item;

public class Vehicle extends Item {
    private int mileage;

    public Vehicle(String id, String name, double startingPrice, String endTime, String sellerId, int mileage) {
        super(id, name, startingPrice, endTime, sellerId);
        this.mileage = mileage;
    }

    public int getMileage() { return mileage; }

    @Override
    public String getType() { return "VEHICLE"; }

    @Override
    public String showDetails() {
        return "[Vehicle] " + getName() + " | Odo: " + mileage + " km";
    }
}