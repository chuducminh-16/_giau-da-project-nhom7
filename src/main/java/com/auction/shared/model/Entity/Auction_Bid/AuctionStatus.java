package com.auction.shared.model.Entity.Auction_Bid;

/**
 * Trạng thái vòng đời của một phiên đấu giá.
 * OPEN → RUNNING → FINISHED → PAID | CANCELED
 */
public enum AuctionStatus {
    OPEN,       // Vừa tạo, chưa bắt đầu
    RUNNING,    // Đang diễn ra
    FINISHED,   // Hết giờ, chưa thanh toán
    PAID,       // Người thắng đã thanh toán
    CANCELED    // Bị hủy
}
