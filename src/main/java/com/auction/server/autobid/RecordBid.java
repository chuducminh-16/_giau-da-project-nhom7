package com.auction.server.autobid;

/**
 * Event được publish lên BidAuto mỗi khi có bid thành công.
 * Immutable — an toàn với multi-thread.
 *
 * ClientHandler publish event này SAU KHI placeBid() thành công,
 * BidAuto sẽ nhận và trigger AutoBidService độc lập.
 */
public record RecordBid(
        String productId,    // ID sản phẩm vừa có bid
        String bidderId,     // ID người vừa bid (để loại khỏi auto-bid)
        double newPrice      // Giá mới sau bid
) {}
