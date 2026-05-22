package com.auction.shared.model.Entity.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Seller — nguoi ban / dang san pham dau gia.
 *
 * FIX: xoa override save() nem UnsupportedOperationException.
 */
public class Seller extends User {

    private double       rating;
    private List<String> itemIds;

    public Seller(String id, String username, String email, String password, double rating) {
        super(id, username, email, password);
        this.rating  = 5.0;
        this.itemIds = new ArrayList<>();
    }

    /** Constructor rong cho Gson */
    public Seller() {
        this.itemIds = new ArrayList<>();
    }

    public double getRating() { return rating; }

    public void setRating(double rating) {
        if (rating >= 0 && rating <= 5) this.rating = rating;
    }

    public void addItem(String itemId) {
        if (itemId != null && !itemIds.contains(itemId)) itemIds.add(itemId);
    }

    public List<String> getItemIds() {
        return Collections.unmodifiableList(itemIds);
    }

    @Override
    public void displayRole() {
        System.out.println("[Seller] Username: " + getUsername()
                + " | Rating: " + rating + " sao");
    }

    @Override
    public String getRole() { return "SELLER"; }
}
