package com.auction.pattern.observer;

import com.auction.model.Entity.Auction_Bid.Bid;
import java.time.LocalDateTime;

/**
 * Dữ liệu được truyền qua Observer khi có bid mới.
 * Immutable — thread-safe để broadcast cho nhiều client.
 */
public final class BidEvent {

    private final long          auctionId;
    private final String        productId;
    private final Bid           bid;           // Bid vừa được chấp nhận
    private final double        newPrice;      // Giá mới sau bid
    private final String        bidderName;    // Tên người đặt (đã resolve)
    private final LocalDateTime timestamp;

    public BidEvent(long auctionId, String productId,
                    Bid bid, double newPrice, String bidderName) {
        this.auctionId  = auctionId;
        this.productId  = productId;
        this.bid        = bid;
        this.newPrice   = newPrice;
        this.bidderName = bidderName;
        this.timestamp  = LocalDateTime.now();
    }

    public long          getAuctionId()  { return auctionId; }
    public String        getProductId()  { return productId; }
    public Bid           getBid()        { return bid; }
    public double        getNewPrice()   { return newPrice; }
    public String        getBidderName() { return bidderName; }
    public LocalDateTime getTimestamp()  { return timestamp; }
}
