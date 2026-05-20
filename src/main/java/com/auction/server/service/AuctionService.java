package com.auction.server.service;

import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.ProductDAO;
import com.auction.shared.model.Entity.Auction_Bid.Auction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class AuctionService {

    private final ProductDAO productDAO = new ProductDAO();

    // ── THÊM MỚI ──────────────────────────────────────
    private final BidDAO bidDAO = new BidDAO();

    // Lock riêng cho từng phiên — chống race condition khi bid
    private final ConcurrentHashMap<String, ReentrantLock> locks =
            new ConcurrentHashMap<>();

    // ══════════════════════════════════════════
    // GIỮ NGUYÊN — CODE CŨ
    // ══════════════════════════════════════════

    // ── Lấy sản phẩm của seller ────────────────────────
    public List<Auction> getProductsBySeller(String sellerId) {
        return productDAO.findBySeller(sellerId);
    }

    // ── Lấy tất cả phiên đang active ──────────────────
    public List<Auction> getActiveAuctions() {
        return productDAO.findAllActive();
    }

    // ── Thêm sản phẩm ─────────────────────────────────
    public Auction addProduct(String sellerId, String name,
                              String description, double startPrice,
                              double bidIncrement, String imagePath,
                              LocalDateTime startTime,
                              LocalDateTime endTime) {
        if (startPrice <= 0)
            throw new IllegalArgumentException("Giá khởi điểm phải > 0");
        if (endTime.isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Thời gian kết thúc đã qua");

        return productDAO.save(sellerId, name, description,
                startPrice, bidIncrement,
                imagePath, startTime, endTime);
    }

    // ── Cập nhật sản phẩm ─────────────────────────────
    public boolean updateProduct(String productId, String name,
                                 String description, double startPrice,
                                 double bidIncrement,
                                 LocalDateTime endTime) {
        return productDAO.update(productId, name, description,
                startPrice, bidIncrement, endTime);
    }

    // ── Xoá sản phẩm ──────────────────────────────────
    public boolean deleteProduct(String productId) {
        return productDAO.delete(productId);
    }

    // ══════════════════════════════════════════
    // BỔ SUNG MỚI — placeBid() + getBidHistory()
    // ══════════════════════════════════════════

    // ── Enum kết quả bid ──────────────────────────────
    public enum BidResult {
        SUCCESS,
        PRICE_TOO_LOW,       // giá đặt thấp hơn hoặc bằng giá hiện tại
        AUCTION_ENDED,       // phiên đã hết giờ
        AUCTION_NOT_FOUND,   // không tìm thấy sản phẩm
        ERROR                // lỗi hệ thống
    }

    // ── Record trả về cho ClientHandler ──────────────
    public record BidOutcome(
            BidResult result,
            double    newBid,    // giá mới nếu SUCCESS, giá hiện tại nếu thất bại
            String    message    // thông báo hiển thị cho user
    ) {}

    /**
     * Đặt giá — thread-safe nhờ ReentrantLock per product.
     *
     * Luồng:
     *   ClientHandler nhận PLACE_BID
     *       → gọi placeBid()
     *           → lock(productId)
     *           → validate
     *           → bidDAO.save() + bidDAO.updateCurrentBid()
     *           → unlock
     *       → broadcast BID_UPDATE nếu SUCCESS
     */
    public BidOutcome placeBid(String productId, String bidderId,
                               double amount) {

        // Lấy lock riêng cho product — computeIfAbsent là atomic
        ReentrantLock lock = locks.computeIfAbsent(
                productId, k -> new ReentrantLock()
        );

        lock.lock();
        try {
            // 1. Kiểm tra sản phẩm tồn tại
            Auction auction = productDAO.findById(productId);
            if (auction == null)
                return new BidOutcome(BidResult.AUCTION_NOT_FOUND,
                        0, "Sản phẩm không tồn tại.");

            // 2. Kiểm tra phiên còn hạn
            if (auction.getEndTime() != null &&
                    LocalDateTime.now().isAfter(auction.getEndTime()))
                return new BidOutcome(BidResult.AUCTION_ENDED,
                        0, "Phiên đấu giá đã kết thúc.");

            // 3. Lấy giá hiện tại thật từ DB — không dùng cache
            //    Fallback về starting_price nếu chưa có bid nào
            double currentBid = bidDAO.getCurrentBid(productId);
            if (currentBid == 0)
                currentBid = auction.getCurrentPrice();

            // 4. Validate giá — phải cao hơn giá hiện tại
            if (amount <= currentBid)
                return new BidOutcome(BidResult.PRICE_TOO_LOW,
                        currentBid,
                        String.format("Giá phải cao hơn %.0f VNĐ",
                                currentBid));

            // 5. Lưu bid vào bảng bids
            boolean savedBid = bidDAO.save(productId, bidderId, amount);
            if (!savedBid)
                return new BidOutcome(BidResult.ERROR,
                        currentBid, "Lỗi lưu bid, thử lại.");

            // 6. Cập nhật current_bid trên bảng items/products
            boolean updated = bidDAO.updateCurrentBid(
                    productId, amount, bidderId);
            if (!updated)
                return new BidOutcome(BidResult.ERROR,
                        currentBid, "Lỗi cập nhật giá, thử lại.");

            // ✅ Thành công
            return new BidOutcome(BidResult.SUCCESS, amount,
                    "Đặt giá thành công!");

        } finally {
            lock.unlock(); // LUÔN unlock dù có exception
        }
    }

    /**
     * Lấy lịch sử bid của 1 sản phẩm.
     * Dùng khi client gửi GET_BID_HISTORY.
     */
    public List<BidDAO.BidRecord> getBidHistory(String productId) {
        return bidDAO.getBidRecords(productId);
    }
}