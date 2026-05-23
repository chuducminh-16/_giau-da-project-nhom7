package com.auction.client.session;

/**
 * Singleton — lưu ID của item đang được xem.
 * Chỉ lưu String itemId, KHÔNG cache Item object.
 * Detail/LiveBidding tự fetch fresh data từ server khi cần.
 */
public class SelectedProductSession {

    private static SelectedProductSession instance;

    public static SelectedProductSession getInstance() {
        if (instance == null) instance = new SelectedProductSession();
        return instance;
    }

    private SelectedProductSession() {}

    private String selectedItemId;

    public void setProductId(String itemId) { this.selectedItemId = itemId; }
    public String getProductId()            { return selectedItemId; }
    public void clear()                     { selectedItemId = null; }
}