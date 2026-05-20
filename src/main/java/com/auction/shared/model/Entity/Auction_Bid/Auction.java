package com.auction.shared.model.Entity.Auction_Bid;

import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.User.Seller;

import java.time.LocalDateTime;

/**
 * Quản lý trung tâm một phiên đấu giá.
 * Fix: thêm AuctionStatus, đảm bảo endTime là LocalDateTime.
 */
public class Auction {

    private final long          id;
    private final Item          item;
    private final Seller        seller;
    private double              currentPrice;
    private String              leadingBidderId;   // ID người đang dẫn đầu
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private AuctionStatus       status;

    public Auction(long id, Item item, Seller seller,
                   double startPrice, LocalDateTime endTime) {
        this(id, item, seller, startPrice, LocalDateTime.now(), endTime);
    }

    public Auction(long id, Item item, Seller seller,
                   double startPrice,
                   LocalDateTime startTime, LocalDateTime endTime) {
        this.id           = id;
        this.item         = item;
        this.seller       = seller;
        this.currentPrice = startPrice;
        this.startTime    = startTime;
        this.endTime      = endTime;
        this.status       = AuctionStatus.OPEN;
    }

    // ── Business logic ───────────────────────────────────────────────────

    /** Tự động cập nhật status dựa theo thời gian hiện tại. */
    public void refreshStatus() {
        if (status == AuctionStatus.CANCELED || status == AuctionStatus.PAID) return;

        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && now.isBefore(startTime)) {
            status = AuctionStatus.OPEN;
        } else if (endTime != null && now.isAfter(endTime)) {
            status = AuctionStatus.FINISHED;
        } else {
            status = AuctionStatus.RUNNING;
        }
    }

    /** Kiểm tra phiên còn đang nhận bid không. */
    public boolean isActive() {
        refreshStatus();
        return status == AuctionStatus.RUNNING || status == AuctionStatus.OPEN;
    }

    // ── Getters / Setters ────────────────────────────────────────────────

    public long          getId()              { return id; }
    public Item          getItem()            { return item; }
    public Seller        getSeller()          { return seller; }
    public double        getCurrentPrice()    { return currentPrice; }
    public String        getLeadingBidderId() { return leadingBidderId; }
    public LocalDateTime getStartTime()       { return startTime; }
    public LocalDateTime getEndTime()         { return endTime; }
    public AuctionStatus getStatus()          { return status; }

    public void setCurrentPrice(double price)          { this.currentPrice     = price; }
    public void setLeadingBidderId(String bidderId)    { this.leadingBidderId  = bidderId; }
    public void setStatus(AuctionStatus status)        { this.status           = status; }

    /** Setter nhận String từ DB — chuyển về enum an toàn. */
    public void setStatus(String statusStr) {
        try {
            this.status = AuctionStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            this.status = AuctionStatus.OPEN;
        }
    }

    @Override
    public String toString() {
        return String.format("Auction{id=%d, item='%s', price=%.0f, status=%s, end=%s}",
                id, item.getName(), currentPrice, status, endTime);
    }
}