package com.auction.model.Entity.Item;

public class Art extends Item {
    private String artist;

    public Art(String id, String name, double startingPrice, String endTime, String sellerId, String artist) {
        super(id, name, startingPrice, endTime, sellerId);
        this.artist = artist;
    }

    public String getArtist() { return artist; }

    @Override
    public String getType() { return "ART"; }

    @Override
    public String showDetails() {
        return "[Art] " + getName() + " | Họa sĩ: " + artist;
    }
}