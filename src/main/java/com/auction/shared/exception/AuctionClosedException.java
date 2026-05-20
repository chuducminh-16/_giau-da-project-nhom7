package com.auction.shared.exception;

import com.auction.shared.model.Entity.Auction_Bid.AuctionStatus;

/**
 * Ném khi cố đặt giá vào một phiên đã đóng (FINISHED / CANCELED / PAID).
 */
public class AuctionClosedException extends AuctionException {

    private final long          auctionId;
    private final AuctionStatus currentStatus;

    public AuctionClosedException(long auctionId, AuctionStatus status) {
        super("AUCTION_CLOSED", String.format(
                "Phiên đấu giá #%d đã đóng (trạng thái: %s).", auctionId, status));
        this.auctionId     = auctionId;
        this.currentStatus = status;
    }

    public AuctionClosedException(String message) {
        super("AUCTION_CLOSED", message);
        this.auctionId     = -1;
        this.currentStatus = AuctionStatus.FINISHED;
    }

    public long          getAuctionId()     { return auctionId; }
    public AuctionStatus getCurrentStatus() { return currentStatus; }
}
