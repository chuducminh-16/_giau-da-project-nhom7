package com.auction.shared.pattern.observer;

/**
 * Observer interface — implement để nhận thông báo khi có bid mới.
 *
 * Đây là phần Observer Pattern bắt buộc theo đề bài (realtime bid notify).
 * Các class implement: ClientHandler (gửi socket), AutoBidHandler, v.v.
 */
public interface AuctionObserver {

    /**
     * Được gọi ngay khi có bid mới hợp lệ trong phiên đang theo dõi.
     *
     * @param event Thông tin đầy đủ về bid vừa xảy ra
     */
    void onBidPlaced(BidEvent event);

    /**
     * Được gọi khi phiên đấu giá kết thúc (hết giờ hoặc bị hủy).
     *
     * @param auctionId  ID phiên vừa đóng
     * @param winnerId   ID người thắng (null nếu không có bid nào)
     * @param finalPrice Giá cuối cùng
     */
    void onAuctionClosed(long auctionId, String winnerId, double finalPrice);
}
