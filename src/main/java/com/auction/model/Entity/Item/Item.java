package com.auction.model.Entity.Item;

import com.auction.model.Entity.Entity;
import java.time.LocalDateTime;

public abstract class Item extends Entity {

    private final double        startingPrice;
    private final LocalDateTime endTime;       // đổi từ String → LocalDateTime
    private final String        sellerId;

    public Item(String id, String name, double startingPrice,
                LocalDateTime endTime, String sellerId) {
        super(id, name);
        this.startingPrice = startingPrice;
        this.endTime       = endTime;
        this.sellerId      = sellerId;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public double        getStartingPrice() { return startingPrice; }
    public LocalDateTime getEndTime()       { return endTime; }
    public String        getSellerId()      { return sellerId; }

    // ── Abstract methods — subclass phải override ─────────────────────────

    public abstract String getType();
    public abstract String showDetails();
}