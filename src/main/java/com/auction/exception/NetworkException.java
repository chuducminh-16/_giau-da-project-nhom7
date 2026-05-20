package com.auction.exception;

/**
 * Ném khi có lỗi kết nối mạng (lỗi kết nối theo đề bài).
 * Dùng trong: ClientHandler khi socket bị ngắt bất ngờ,
 *             NetworkServer khi không thể bind port.
 */
public class NetworkException extends AuctionException {

    private final int port;

    public NetworkException(String message, Throwable cause) {
        super("NETWORK_ERROR", message, cause);
        this.port = -1;
    }

    public NetworkException(int port, Throwable cause) {
        super("NETWORK_ERROR",
                "Không thể kết nối tới server trên cổng " + port + ": " + cause.getMessage(),
                cause);
        this.port = port;
    }

    public NetworkException(String message) {
        super("NETWORK_ERROR", message);
        this.port = -1;
    }

    public int getPort() { return port; }
}
