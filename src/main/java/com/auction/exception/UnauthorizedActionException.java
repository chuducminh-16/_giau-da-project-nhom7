package com.auction.exception;

/**
 * Ném khi người dùng thực hiện hành động không được phép.
 * Ví dụ:
 *  - Bidder cố xóa sản phẩm của Seller khác
 *  - Seller tự bid vào phiên của chính mình
 *  - Chưa đăng nhập mà cố đặt giá
 */
public class UnauthorizedActionException extends AuctionException {

    private final String userId;
    private final String action;

    public UnauthorizedActionException(String userId, String action) {
        super("UNAUTHORIZED",
                String.format("Người dùng '%s' không có quyền thực hiện: %s", userId, action));
        this.userId = userId;
        this.action = action;
    }

    public UnauthorizedActionException(String message) {
        super("UNAUTHORIZED", message);
        this.userId = null;
        this.action = null;
    }

    public String getUserId() { return userId; }
    public String getAction() { return action; }
}
