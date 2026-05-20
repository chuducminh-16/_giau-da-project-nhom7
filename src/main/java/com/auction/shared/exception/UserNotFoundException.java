package com.auction.shared.exception;

/**
 * Ném khi không tìm thấy user trong hệ thống.
 * Ví dụ: login sai email, truy vấn user không tồn tại.
 */
public class UserNotFoundException extends AuctionException {

    private final String identifier;

    public UserNotFoundException(String identifier) {
        super("USER_NOT_FOUND",
                "Không tìm thấy người dùng: " + identifier);
        this.identifier = identifier;
    }

    public String getIdentifier() { return identifier; }
}
