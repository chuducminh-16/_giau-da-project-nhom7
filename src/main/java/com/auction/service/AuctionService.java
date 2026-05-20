package com.auction.service;

import com.auction.dao.auction.AuctionDAO;
import com.auction.dao.item.ItemSaveDAO;
import com.auction.dao.transaction.TransactionDAO;
import com.auction.model.Entity.Item.Item;
import com.auction.model.Entity.User.Seller;

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
    }
}