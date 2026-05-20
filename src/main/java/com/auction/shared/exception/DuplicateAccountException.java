package com.auction.shared.exception;

/**
 * Ném khi đăng ký tài khoản bị trùng email hoặc username.
 */
public class DuplicateAccountException extends AuctionException {

    public enum Field { EMAIL, USERNAME }

    private final Field  field;
    private final String value;

    public DuplicateAccountException(Field field, String value) {
        super("DUPLICATE_ACCOUNT", String.format(
                "%s '%s' đã được sử dụng.",
                field == Field.EMAIL ? "Email" : "Tên đăng nhập", value));
        this.field = field;
        this.value = value;
    }

    public Field  getField() { return field; }
    public String getValue() { return value; }
}
