package com.auction.shared.exception;

/**
 * Ném khi giá đặt không hợp lệ:
 *  - Thấp hơn hoặc bằng giá hiện tại
 *  - Không đủ bước giá tối thiểu (bidIncrement)
 */
public class InvalidBidException extends AuctionException {

    private final double currentPrice;
    private final double attemptedPrice;

    public InvalidBidException(double currentPrice, double attemptedPrice) {
        super("INVALID_BID", String.format(
                "Giá đặt %.0f VNĐ không hợp lệ — phải cao hơn giá hiện tại %.0f VNĐ.",
                attemptedPrice, currentPrice));
        this.currentPrice   = currentPrice;
        this.attemptedPrice = attemptedPrice;
    }

    public InvalidBidException(String message) {
        super("INVALID_BID", message);
        this.currentPrice   = 0;
        this.attemptedPrice = 0;
    }

    public double getCurrentPrice()   { return currentPrice; }
    public double getAttemptedPrice() { return attemptedPrice; }
}
