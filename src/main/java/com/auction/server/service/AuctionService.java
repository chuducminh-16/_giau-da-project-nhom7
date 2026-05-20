package com.auction.server.service;

<<<<<<< HEAD
import com.auction.dao.bid.BidDAO;
import com.auction.server.dao.ProductDAO;
import com.auction.model.Entity.Auction_Bid.Auction;

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
=======
import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.item.ItemSaveDAO;
import com.auction.server.dao.transaction.TransactionDAO;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.User.Seller;

import java.util.List;
import java.util.Map;

public class AuctionService {

    private final AuctionDAO     auctionDAO     = new AuctionDAO();
    private final ItemSaveDAO    itemSaveDAO    = new ItemSaveDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    // ------------------------------------------------------------------ //
    //  TẠO PHIÊN MỚI                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Seller tạo phiên đấu giá mới.
     *
     * Luồng:
     * 1. Lưu Item vào bảng items
     * 2. Tạo phiên trong bảng auctions
     *
     * @param item    Sản phẩm đưa ra đấu giá
     * @param seller  Người bán
     * @param endTime Thời gian kết thúc (VD: "2026-12-31 23:59:59")
     * @return ID phiên nếu tạo thành công, -1 nếu thất bại
     */
    public long createAuction(Item item, Seller seller, String endTime) {

        // Validate
        if (item == null || seller == null || endTime == null) {
            System.out.println("Lỗi: Thiếu thông tin để tạo phiên!");
            return -1;
        }
        if (item.getStartingPrice() <= 0) {
            System.out.println("Lỗi: Giá khởi điểm phải lớn hơn 0!");
            return -1;
        }

        // Bước 1: Lưu sản phẩm vào bảng items
        boolean itemSaved = itemSaveDAO.saveItem(item);
        if (!itemSaved) {
            System.out.println("Lỗi: Không thể lưu sản phẩm!");
            return -1;
        }

        // Bước 2: Sinh auction ID bằng timestamp
        // System.currentTimeMillis() đảm bảo unique vì mỗi ms chỉ có 1 giá trị
        long auctionId = System.currentTimeMillis();

        // Bước 3: Lưu phiên vào bảng auctions
        boolean auctionSaved = auctionDAO.saveAuction(
            auctionId,
            item.getId(),
            seller.getId(),
            item.getStartingPrice(),
            endTime
        );

        if (!auctionSaved) {
            System.out.println("Lỗi: Không thể tạo phiên đấu giá!");
            return -1;
        }

        System.out.println("Tạo phiên thành công! ID: " + auctionId);
        return auctionId;
    }

    // ------------------------------------------------------------------ //
    //  KẾT THÚC PHIÊN                                                     //
    // ------------------------------------------------------------------ //

    /**
     * Kết thúc phiên đấu giá — đây là hàm quan trọng nhất.
     *
     * Luồng:
     * 1. Tìm người thắng (bid cao nhất)
     * 2. Nếu có người thắng → lưu Transaction → đóng phiên FINISHED
     * 3. Nếu không có ai đặt giá → đóng phiên CANCELED
     *
     * @param auctionId ID phiên cần kết thúc
     * @return Map chứa thông tin người thắng, null nếu không có ai đặt giá
     */
    public Map<String, Object> endAuction(long auctionId) {

        // Bước 1: Kiểm tra phiên tồn tại không
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) {
            System.out.println("Lỗi: Không tìm thấy phiên #" + auctionId);
            return null;
        }

        // Bước 2: Kiểm tra phiên chưa bị đóng trước đó
        String status = (String) auction.get("status");
        if (status.equals("FINISHED") || status.equals("CANCELED") || status.equals("PAID")) {
            System.out.println("Lỗi: Phiên đã kết thúc rồi! (status: " + status + ")");
            return null;
        }

        // Bước 3: Tìm người thắng
        Map<String, Object> winner = auctionDAO.findWinner(auctionId);

        if (winner == null) {
            // Không có ai đặt giá → CANCELED
            auctionDAO.updateStatus(auctionId, "CANCELED");
            System.out.println("Phiên #" + auctionId + " kết thúc — không có người đặt giá. CANCELED.");
            return null;
        }

        // Bước 4: Có người thắng → lưu Transaction
        String itemId    = (String) auction.get("itemId");
        String winnerId  = (String) winner.get("bidderId");
        double finalPrice = (double) winner.get("finalPrice");

        transactionDAO.saveTransaction(itemId, winnerId, finalPrice);

        // Bước 5: Cập nhật trạng thái phiên → FINISHED
        auctionDAO.updateStatus(auctionId, "FINISHED");

        System.out.println("Phiên #" + auctionId + " kết thúc!");
        System.out.println("Người thắng: " + winner.get("username") + " — Giá: " + finalPrice);

        return winner; // trả về thông tin người thắng cho Controller hiển thị
    }

    // ------------------------------------------------------------------ //
    //  XEM PHIÊN                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Lấy danh sách phiên đang mở — dùng cho màn hình Home.
     */
    public List<Map<String, Object>> getOpenAuctions() {
        return auctionDAO.findAllOpen();
    }

    /**
     * Lấy tất cả phiên — dùng cho màn hình Admin.
     */
    public List<Map<String, Object>> getAllAuctions() {
        return auctionDAO.findAll();
    }

    /**
     * Lấy phiên theo Seller — dùng cho màn hình ManageProduct.
     */
    public List<Map<String, Object>> getAuctionsBySeller(String sellerId) {
        return auctionDAO.findBySeller(sellerId);
    }

    /**
     * Lấy chi tiết 1 phiên — dùng cho màn hình Detail.
     */
    public Map<String, Object> getAuctionById(long auctionId) {
        return auctionDAO.findById(auctionId);
>>>>>>> f8f268f89cbfbd54731738e4b358cbe1b4ac4b0a
    }
}