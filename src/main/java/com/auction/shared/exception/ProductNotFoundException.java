package com.auction.shared.exception;

/**
 * Ném khi không tìm thấy sản phẩm / phiên đấu giá theo ID.
 */
public class ProductNotFoundException extends AuctionException {

    private final String productId;

    public ProductNotFoundException(String productId) {
        super("PRODUCT_NOT_FOUND",
                "Không tìm thấy sản phẩm với ID: " + productId);
        this.productId = productId;
    }

    public String getProductId() { return productId; }
}
