package com.auction.exception;

/**
 * Ném khi dữ liệu sản phẩm đầu vào không hợp lệ (lỗi dữ liệu theo đề bài).
 * Ví dụ:
 *  - Tên sản phẩm rỗng
 *  - Giá khởi điểm <= 0
 *  - Thời gian kết thúc đã qua
 *  - Bước giá âm
 */
public class InvalidProductDataException extends AuctionException {

    private final String field;
    private final Object value;

    public InvalidProductDataException(String field, Object value, String reason) {
        super("INVALID_PRODUCT_DATA",
                String.format("Dữ liệu sản phẩm không hợp lệ — trường '%s' = '%s': %s",
                        field, value, reason));
        this.field = field;
        this.value = value;
    }

    public InvalidProductDataException(String message) {
        super("INVALID_PRODUCT_DATA", message);
        this.field = null;
        this.value = null;
    }

    public String getField() { return field; }
    public Object getValue() { return value; }
}
