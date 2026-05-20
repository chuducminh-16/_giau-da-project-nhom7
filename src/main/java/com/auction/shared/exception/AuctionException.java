package com.auction.shared.exception;

/**
 * Lớp cha của tất cả exception trong hệ thống đấu giá.
 * Cho phép catch tổng quát: catch (AuctionException e)
 */
public abstract class AuctionException extends RuntimeException {

    private final String errorCode;

    public AuctionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AuctionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
