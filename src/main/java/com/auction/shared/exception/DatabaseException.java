package com.auction.shared.exception;

/**
 * Ném khi có lỗi tầng database (lỗi dữ liệu theo đề bài).
 * Wrap SQLException thành unchecked exception để tầng Service
 * không cần khai báo throws SQLException.
 *
 * Dùng trong: tất cả DAO khi executeUpdate/executeQuery thất bại.
 */
public class DatabaseException extends AuctionException {

    public DatabaseException(String operation, Throwable cause) {
        super("DB_ERROR",
                "Lỗi database khi thực hiện [" + operation + "]: " + cause.getMessage(),
                cause);
    }

    public DatabaseException(String message) {
        super("DB_ERROR", message);
    }
}
