package com.auction.shared.model.Entity.Item;

import java.time.LocalDateTime;

public class Art extends Item {

    private String artist; // tên nghệ sĩ / tác giả

    public Art(String id, String name, double startingPrice,
               LocalDateTime endTime, String sellerId,
               String artist) {
        super(id, name, startingPrice, endTime, sellerId);
        this.artist = artist;
    }

    /** Overload nhận String endTime — tự parse để tương thích code cũ. */
    public Art(String id, String name, double startingPrice,
               String endTime, String sellerId,
               String artist) {
        super(id, name, startingPrice,
                endTime != null ? LocalDateTime.parse(endTime) : null,
                sellerId);
        this.artist = artist;
    }

    public String getArtist() { return artist; }

    @Override
    public String getType() { return "ART"; }

    @Override
    public String showDetails() {
        return "[Art] " + getName() + " | Tác giả: " + artist;
    }
}