package com.auction.client.session;

import com.auction.client.model.Product;

/**
 * Singleton lưu sản phẩm đang được chọn.
 * Home set → Detail/LiveBidding get.
 */
public class SelectedProductSession {

    private static SelectedProductSession instance;

    public static SelectedProductSession getInstance() {
        if (instance == null) instance = new SelectedProductSession();
        return instance;
    }

    private SelectedProductSession() {}

    private Product selectedProduct;

    public void setProduct(Product product) {
        this.selectedProduct = product;
    }

    public Product getProduct() {
        return selectedProduct;
    }

    public void clear() {
        selectedProduct = null;
    }
}