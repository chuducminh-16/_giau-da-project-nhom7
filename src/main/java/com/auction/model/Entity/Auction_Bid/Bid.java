package com.auction.model.Entity.Auction_Bid;

import java.time.LocalDateTime;

/**
 * Đại diện cho một lần đặt giá trong phiên đấu giá.
 * Fix: thêm id, timestamp, itemId theo yêu cầu đề bài.
 */
public class Bid {

    private final String        id;          // UUID của lần bid này
    private final String        itemId;      // ID sản phẩm đang đấu giá
    private final String        bidderId;    // ID người đặt giá
    private final double        amount;      // Số tiền đặt giá
    private final LocalDateTime timestamp;   // Thời điểm đặt giá

    public Bid(String id, String itemId, String bidderId, double amount) {
        this.id        = id;
        this.itemId    = itemId;
        this.bidderId  = bidderId;
        this.amount    = amount;
        this.timestamp = LocalDateTime.now();
    }

    /** Constructor cho BidDAO khi đọc từ DB (có timestamp cụ thể). */
    public Bid(String id, String itemId, String bidderId,
               double amount, LocalDateTime timestamp) {
        this.id        = id;
        this.itemId    = itemId;
        this.bidderId  = bidderId;
        this.amount    = amount;
        this.timestamp = timestamp;
    }

    // ── Getters ─────────────────────────────────────────────────────────

    public String        getId()        { return id; }
    public String        getItemId()    { return itemId; }
    public String        getBidderId()  { return bidderId; }
    public double        getAmount()    { return amount; }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("Bid{id='%s', itemId='%s', bidderId='%s', amount=%.0f, time=%s}",
                id, itemId, bidderId, amount, timestamp);
    }
}