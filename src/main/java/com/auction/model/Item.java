package com.auction.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public abstract class Item implements Serializable {
    private String id;
    private String name;
    private double currentPrice;
    private LocalDateTime endTime;

    public Item(String id, String name, double price, LocalDateTime endTime) {
        this.id = id;
        this.name = name;
        this.currentPrice = price;
        this.endTime = endTime;
    }

    // Encapsulation: Getter/Setter [cite: 653]
    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double price) { this.currentPrice = price; }
    public String getName() { return name; }

    // Abstraction: Mỗi loại hàng có mô tả riêng [cite: 656]
    public abstract String getCategoryInfo();
}