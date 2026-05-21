package com.auction.shared.model.Entity.Item;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.auction.shared.model.Entity.Entity;

/**
 * Abstract class đại diện cho sản phẩm đấu giá.
 * Dùng chung cho cả Server (DAO) và Client (Controller/UI).
 * Implement Serializable để Gson có thể serialize/deserialize qua socket.
 */
public abstract class Item extends Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    // ── Fields gốc ───────────────────────────────────────────────────────
    private final double        startingPrice;
    private final LocalDateTime endTime;
    private final String        sellerId;

    // ── Fields bổ sung (trước ở Product, nay chuyển vào Item) ────────────
    private String        sellerName;      // tên người bán (hiển thị UI)
    private double        currentBid;      // giá đặt cao nhất hiện tại
    private double        bidIncrement;    // bước giá tối thiểu
    private String        imagePath;       // đường dẫn ảnh
    private String        status;          // PENDING | OPEN | RUNNING | FINISHED | CANCELED | PAID
    private LocalDateTime startTime;       // thời gian bắt đầu phiên

    // ── Constructor đầy đủ ───────────────────────────────────────────────
    public Item(String id, String name, double startingPrice,
                LocalDateTime endTime, String sellerId) {
        super(id, name);
        this.startingPrice = startingPrice;
        this.currentBid    = startingPrice; // mặc định = giá khởi điểm
        this.endTime       = endTime;
        this.sellerId      = sellerId;
        this.status        = "OPEN";
    }

    // ── Abstract methods — subclass phải override ────────────────────────
    public abstract String getType();
    public abstract String showDetails();

    // ── Getters gốc ─────────────────────────────────────────────────────
    public double        getStartingPrice() { return startingPrice; }
    public LocalDateTime getEndTime()       { return endTime; }
    public String        getSellerId()      { return sellerId; }

    // ── Getters/Setters bổ sung ──────────────────────────────────────────
    public String getSellerName()                  { return sellerName; }
    public void   setSellerName(String sellerName) { this.sellerName = sellerName; }

    public double getCurrentBid() {
        return currentBid > 0 ? currentBid : startingPrice;
    }
    public void setCurrentBid(double currentBid) {
        this.currentBid = currentBid;
    }

    public double getBidIncrement()                    { return bidIncrement; }
    public void   setBidIncrement(double bidIncrement) { this.bidIncrement = bidIncrement; }

    public String getImagePath()                 { return imagePath; }
    public void   setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getStatus()               { return status; }
    public void   setStatus(String status)  { this.status = status; }

    public LocalDateTime getStartTime()                   { return startTime; }
    public void          setStartTime(LocalDateTime time) { this.startTime = time; }

    // ── Helper methods (trước ở Product) ────────────────────────────────

    /**
     * Format thời gian kết thúc → "dd/MM/yyyy HH:mm" dùng cho TableView.
     */
    public String getFormattedEndTime() {
        if (endTime == null) return "";
        return endTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /**
     * Format giá hiện tại dạng VNĐ dùng cho TableView.
     */
    public String getFormattedPrice() {
        return String.format("%,.0f VNĐ", getCurrentBid());
    }

    /**
     * Số giây còn lại đến khi phiên kết thúc.
     * Dùng cho đồng hồ đếm ngược ở LiveBiddingController.
     */
    public long getSecondsRemaining() {
        if (endTime == null) return 0;
        long secs = java.time.Duration
                .between(LocalDateTime.now(), endTime)
                .getSeconds();
        return Math.max(0, secs);
    }

    /**
     * Kiểm tra phiên có đang diễn ra không.
     */
    public boolean isActive() {
        return ("OPEN".equals(status) || "RUNNING".equals(status))
                && getSecondsRemaining() > 0;
    }

    @Override
    public String toString() {
        return String.format("Item{id='%s', name='%s', type='%s', status='%s', currentBid=%.0f}",
                getId(), getName(), getType(), status, getCurrentBid());
    }
}