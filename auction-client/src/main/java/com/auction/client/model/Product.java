package com.auction.client.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Class đại diện cho sản phẩm đấu giá.
 * Implement Serializable để có thể gửi đối tượng này qua Socket giữa Client và Server.
 */
public class Product implements Serializable {
    private static final long serialVersionUID = 1L; // Đảm bảo đồng bộ khi truyền nhận đối tượng

    private String id;
    private String name;
    private double startingPrice;
    private double currentPrice;
    private double bidIncrement;
    private String description;
    private String status; // "ACTIVE", "SOLD", "PENDING"
    private LocalDateTime endTime;
    private LocalDateTime startTime;
    private String imagePath;
    private Double currentBid;

    // --- CONSTRUCTORS ---

    // Constructor mặc định
    public Product() {}

    // Constructor đầy đủ dùng khi thêm mới sản phẩm từ Seller Dashboard
    public Product(String name, double startingPrice, double bidIncrement,
                   String description, String status, LocalDateTime endTime, String imagePath, LocalDateTime startTime) {
        this.name = name;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice; // Lúc mới đăng, giá hiện tại bằng giá khởi điểm
        this.bidIncrement = bidIncrement;
        this.description = description;
        this.status = status;
        this.endTime = endTime;
        this.imagePath = imagePath;
        this.startTime = startTime;
    }

    // --- GETTERS AND SETTERS ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getStartingPrice() { return startingPrice; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getBidIncrement() { return bidIncrement; }
    public void setBidIncrement(double bidIncrement) { this.bidIncrement = bidIncrement; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }
    public LocalDateTime getStartTime() {
        return startTime;
    }

    public Double getCurrentBid() {
        return currentBid;
    }
    public void setCurrentBid(Double currentBid) {
        this.currentBid = currentBid;
    }

    // --- HELPER METHODS (Dùng để hiển thị lên TableView đẹp hơn) ---

    /**
     * Trả về chuỗi thời gian kết thúc đã được định dạng dd/MM/yyyy HH:mm
     */
    public String getFormattedEndTime() {
        if (endTime == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return endTime.format(formatter);
    }

    /**
     * Trả về giá tiền có định dạng VNĐ để hiện lên bảng
     */
    public String getFormattedPrice() {
        return String.format("%,.0f VNĐ", currentPrice);
    }

    @Override
    public String toString() {
        return "Product{" + "name='" + name + '\'' + ", status='" + status + '\'' + '}';
    }

    public OffsetDateTime setStartTime() {
        return OffsetDateTime.now();
    }
}