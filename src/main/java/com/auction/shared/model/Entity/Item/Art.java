package com.auction.shared.model.Entity.Item;

import java.time.LocalDateTime;

public class Art extends Item {

    // artist lưu trong description của Item (theo đề bài: artist = description trong Art)
    // Giữ field artist để tương thích code cũ, nhưng sync với description

    public Art(String id, String name, double startingPrice,
               LocalDateTime endTime, String sellerId,
               String artist) {
        super(id, name, startingPrice, endTime, sellerId);
        // artist chính là description trong Art
        setDescription(artist);
    }

    /** Overload nhận String endTime – tự parse để tương thích code cũ. */
    public Art(String id, String name, double startingPrice,
               String endTime, String sellerId,
               String artist) {
        super(id, name, startingPrice,
                endTime != null ? LocalDateTime.parse(endTime) : null,
                sellerId);
        setDescription(artist);
    }

    // Constructor rỗng cho Gson
    public Art() { super(); }

    /** Trả về artist (= description). */
    public String getArtist() { return getDescription(); }
    public void   setArtist(String artist) { setDescription(artist); }

    @Override
    public String getType() { return "ART"; }

    @Override
    public String showDetails() {
        return "[Art] " + getName() + " | Tác giả: " + getDescription();
    }
}
