package com.auction.service;

import com.auction.dao.bid.BidDAO;
import com.auction.dao.item.ItemFindDAO;
import com.auction.dao.auction.AuctionDAO;
import com.auction.model.Entity.Item.Item;

import java.util.List;
import java.util.Map;

public class BidService {

    private final BidDAO     bidDAO     = new BidDAO();
    private final ItemFindDAO itemFindDAO = new ItemFindDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO();

    // ------------------------------------------------------------------ //
    //  ĐẶT GIÁ                                                            //
    // ------------------------------------------------------------------ //

    /**
     * Đặt giá cho 1 phiên đấu giá.
     *
     * Luồng xử lý:
     * 1. Kiểm tra phiên còn mở không
     * 2. Kiểm tra giá đặt có cao hơn giá hiện tại không
     * 3. Lưu bid vào DB
     * 4. Cập nhật giá hiện tại của phiên
     *
     * @param auctionId ID phiên đấu giá
     * @param bidderId  ID người đặt giá
     * @param bidPrice  Giá muốn đặt
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean placeBid(long auctionId, String bidderId, double bidPrice) {

        // Bước 1: Lấy thông tin phiên đấu giá
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) {
            System.out.println("Lỗi: Không tìm thấy phiên đấu giá #" + auctionId);
            return false;
        }

        // Bước 2: Kiểm tra phiên còn mở không
        // Chỉ cho phép đặt giá khi status là OPEN hoặc RUNNING
        String status = (String) auction.get("status");
        if (!status.equals("OPEN") && !status.equals("RUNNING")) {
            System.out.println("Lỗi: Phiên đấu giá đã kết thúc! (status: " + status + ")");
            return false;
        }

        // Bước 3: Kiểm tra giá đặt phải cao hơn giá hiện tại
        double currentPrice = (double) auction.get("currentPrice");
        if (bidPrice <= currentPrice) {
            System.out.println("Lỗi: Giá đặt (" + bidPrice + ") phải cao hơn giá hiện tại (" + currentPrice + ")");
            return false;
        }

        // Bước 4: Lấy item_id từ phiên để lưu bid
        // (bids.item_id vì DB đang dùng item_id thay vì auction_id)
        String itemId = (String) auction.get("itemId");

        // Bước 5: Lưu bid vào bảng bids
        boolean bidSaved = bidDAO.placeBid(itemId, bidderId, bidPrice);
        if (!bidSaved) {
            System.out.println("Lỗi: Không thể lưu bid vào database!");
            return false;
        }

        // Bước 6: Cập nhật giá hiện tại trong bảng auctions
        // → status tự động chuyển sang RUNNING trong AuctionDAO
        boolean priceUpdated = auctionDAO.updateCurrentPrice(auctionId, bidPrice);
        if (!priceUpdated) {
            System.out.println("Cảnh báo: Bid đã lưu nhưng không cập nhật được giá phiên!");
        }

        System.out.println("Đặt giá thành công! Giá mới: " + bidPrice);
        return true;
    }

    // ------------------------------------------------------------------ //
    //  XEM LỊCH SỬ                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Lấy lịch sử đặt giá của 1 phiên.
     * Dùng để hiển thị trên màn hình LiveBidding.
     */
    public List<Map<String, Object>> getBidHistory(long auctionId) {

        // Lấy item_id từ phiên trước
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return List.of(); // trả về list rỗng nếu không tìm thấy

        String itemId = (String) auction.get("itemId");
        return bidDAO.getBidHistory(itemId);
    }

    /**
     * Lấy giá cao nhất hiện tại của 1 phiên.
     */
    public double getHighestBid(long auctionId) {
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return 0;

        String itemId = (String) auction.get("itemId");
        return bidDAO.getHighestBid(itemId);
    }
}