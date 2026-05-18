package com.auction.client.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Class đại diện cho sản phẩm đấu giá.
 * Implement Serializable để có thể gửi qua Socket giữa Client và Server.
 */
public class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    // ── Fields ──────────────────────────────────────────
    private String        id;
    private String        sellerId;       // ← THÊM: id người bán
    private String        sellerName;     // ← THÊM: tên người bán (hiện ở Detail)
    private String        name;
    private String        description;
    private double        startingPrice;
    private double        currentPrice;
    private double        bidIncrement;
    private Double        currentBid;
    private String        imagePath;
    private String        status;         // PENDING | ACTIVE | SOLD
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // ── Constructors ────────────────────────────────────

    /** Constructor mặc định — Gson cần cái này để deserialize */
    public Product() {}

    /**
     * Constructor đầy đủ — dùng khi Seller thêm sản phẩm mới.
     * Giữ nguyên thứ tự tham số cũ để ManageProductController khỏi lỗi.
     */
    public Product(String name, double startingPrice, double bidIncrement,
                   String description, String status,
                   LocalDateTime endTime, String imagePath,
                   LocalDateTime startTime) {
        this.name          = name;
        this.startingPrice = startingPrice;
        this.currentPrice  = startingPrice; // ban đầu = giá khởi điểm
        this.currentBid    = startingPrice; // đồng bộ cả 2 field
        this.bidIncrement  = bidIncrement;
        this.description   = description;
        this.status        = status;
        this.endTime       = endTime;
        this.imagePath     = imagePath;
        this.startTime     = startTime;
    }

    // ── Getters & Setters ───────────────────────────────

    public String getId()               { return id; }
    public void   setId(String id)      { this.id = id; }

    // sellerId + sellerName — THÊM MỚI
    public String getSellerId()                  { return sellerId; }
    public void   setSellerId(String sellerId)   { this.sellerId = sellerId; }

    public String getSellerName()                { return sellerName; }
    public void   setSellerName(String name)     { this.sellerName = name; }

    public String getName()               { return name; }
    public void   setName(String name)    { this.name = name; }

    public String getDescription()                    { return description; }
    public void   setDescription(String description)  { this.description = description; }

    public double getStartingPrice()                      { return startingPrice; }
    public void   setStartingPrice(double startingPrice)  { this.startingPrice = startingPrice; }

    public double getCurrentPrice()                     { return currentPrice; }
    public void   setCurrentPrice(double currentPrice)  { this.currentPrice = currentPrice; }

    public double getBidIncrement()                     { return bidIncrement; }
    public void   setBidIncrement(double bidIncrement)  { this.bidIncrement = bidIncrement; }

    /**
     * currentBid — giá đặt cao nhất hiện tại.
     * Nếu chưa có bid nào thì trả về startingPrice.
     */
    public Double getCurrentBid() {
        return currentBid != null ? currentBid : startingPrice;
    }
    public void setCurrentBid(Double currentBid) {
        this.currentBid   = currentBid;
        this.currentPrice = currentBid; // giữ 2 field đồng bộ
    }

    public String getImagePath()                  { return imagePath; }
    public void   setImagePath(String imagePath)  { this.imagePath = imagePath; }

    public String getStatus()               { return status; }
    public void   setStatus(String status)  { this.status = status; }

    public LocalDateTime getStartTime()                   { return startTime; }
    public void          setStartTime(LocalDateTime time) { this.startTime = time; }

    public LocalDateTime getEndTime()                   { return endTime; }
    public void          setEndTime(LocalDateTime time) { this.endTime = time; }

    // ── Helper methods ──────────────────────────────────

    /**
     * Format thời gian kết thúc để hiện lên TableView.
     * Trả về "dd/MM/yyyy HH:mm" hoặc "" nếu null.
     */
    public String getFormattedEndTime() {
        if (endTime == null) return "";
        return endTime.format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    /**
     * Format giá hiện tại dạng VNĐ để hiện lên TableView.
     */
    public String getFormattedPrice() {
        return String.format("%,.0f VNĐ", getCurrentBid());
    }

    /**
     * Tính số giây còn lại đến khi phiên kết thúc.
     * Dùng cho đồng hồ đếm ngược ở LiveBiddingController.
     * Trả về 0 nếu đã hết giờ hoặc endTime null.
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
        return "ACTIVE".equals(status) && getSecondsRemaining() > 0;
    }

    @Override
    public String toString() {
        return "Product{name='" + name + "', status='" + status
                + "', currentBid=" + getCurrentBid() + "}";
    }
}