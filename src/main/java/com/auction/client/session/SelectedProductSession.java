package com.auction.client.session;

import com.auction.shared.model.Entity.Item.Item;

/**
 * Singleton lưu item đang được chọn.
 * Home set → Detail/LiveBidding get.
 */
public class SelectedProductSession {

    private static SelectedProductSession instance;

    public static SelectedProductSession getInstance() {
        if (instance == null) instance = new SelectedProductSession();
        return instance;
    }

    private SelectedProductSession() {}

    private Item selectedItem;

    public void setProduct(Item item) { this.selectedItem = item; }
    public Item getProduct()          { return selectedItem; }
    public void clear()               { selectedItem = null; }
}