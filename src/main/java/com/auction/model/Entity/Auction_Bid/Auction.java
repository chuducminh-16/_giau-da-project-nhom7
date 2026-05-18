package com.auction.model.Entity.Auction_Bid;

import java.time.chrono.ChronoLocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.LocalDateTime;

import com.auction.model.Entity.Item.Item;
import com.auction.model.Entity.User.Bidder;
import com.auction.model.Entity.User.Seller;

public class Auction {
    private long   id;           // ID phiên đấu giá
    private Item   item;         // vật phẩm
    private Seller seller;       // người bán
    private double currentPrice; // giá hiện tại
    private boolean isActive;    // phiên còn mở không
    private String status;       // OPEN / RUNNING / FINISHED / PAID / CANCELED
    private ChronoLocalDateTime<?> endTime;      // thời gian kết thúc (ISO string)
    private List<Bid> bidHistory; // lịch sử bid

    public Auction(long id, Item item, Seller seller, double currentPrice, ChronoLocalDateTime<?> endTime) {
        this.id           = id;
        this.item         = item;
        this.seller       = seller;
        this.currentPrice = item.getStartingPrice();
        this.isActive     = true;
        this.status       = "OPEN";
        this.endTime      = endTime;
        this.bidHistory   = new ArrayList<>();
    }

    // ------------------------------------------------------------------ //
    //  Nghiệp vụ                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Đặt giá thủ công.
     * @return Bid mới nếu hợp lệ, null nếu không hợp lệ
     * @throws IllegalStateException nếu phiên đã đóng
     * @throws IllegalArgumentException nếu giá không hợp lệ
     */
    public Bid placeBid(Bidder bidder, double amount) {
        if (!isActive) {
            throw new IllegalStateException("Phiên đấu giá đã kết thúc!");
        }
        if (amount <= currentPrice) {
            throw new IllegalArgumentException(
                "Giá đặt (" + amount + ") phải cao hơn giá hiện tại (" + currentPrice + ")");
        }
        Bid newBid = new Bid(bidder, amount);
        bidHistory.add(newBid);
        currentPrice = amount;
        status = "RUNNING";
        return newBid;
    }

    /** Lấy bid cao nhất (bid cuối cùng trong lịch sử). */
    public Bid getHighestBid() {
        if (bidHistory.isEmpty()) return null;
        return bidHistory.get(bidHistory.size() - 1);
    }

    /** Kết thúc phiên, trả về người thắng (null nếu không có bid nào). */
    public Bidder endAuction() {
        this.isActive = false;
        this.status   = "FINISHED";
        Bid highest = getHighestBid();
        return highest != null ? highest.getBidder() : null;
    }

    // ------------------------------------------------------------------ //
    //  Getters / Setters                                                  //
    // ------------------------------------------------------------------ //

    public long    getId()           { return id; }
    public Item    getItem()         { return item; }
    public Seller  getSeller()       { return seller; }
    public double  getCurrentPrice() { return currentPrice; }
    public boolean isActive()        { return isActive; }
    public String  getStatus()       { return status; }
    public ChronoLocalDateTime<?> getEndTime()      { return endTime; }

    public List<Bid> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public void setStatus(String status) { this.status = status; }
    public void setEndTime(ChronoLocalDateTime<?> endTime) { this.endTime = endTime; }

    /** Dùng nội bộ bởi AuctionEngine khi apply auto-bid. */
    public void applyBidDirectly(Bid bid) {
        bidHistory.add(bid);
        currentPrice = bid.getAmount();
        status = "RUNNING";
    }
}
