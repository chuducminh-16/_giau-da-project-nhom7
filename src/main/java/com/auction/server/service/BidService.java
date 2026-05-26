package com.auction.server.service;

import com.auction.server.dao.bid.BidDAO;
import com.auction.server.dao.item.ItemFindDAO;
import com.auction.server.dao.auction.AuctionDAO;
import com.auction.shared.model.Entity.Item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BidService {

    private final BidDAO     bidDAO     = new BidDAO();
    private final ItemFindDAO itemFindDAO = new ItemFindDAO();
    private final AuctionDAO auctionDAO = new AuctionDAO();

    // ------------------------------------------------------------------ //
    //  ĐẶT GIÁ                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Đặt giá cho 1 phiên đấu giá qua ID Số (long).
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
        String itemId = (String) auction.get("itemId");

        // Bước 5: Lưu bid vào bảng bids
        boolean bidSaved = bidDAO.placeBid(itemId, bidderId, bidPrice);
        if (!bidSaved) {
            System.out.println("Lỗi: Không thể lưu bid vào database!");
            return false;
        }

        // Bước 6: Cập nhật giá hiện tại trong bảng auctions
        boolean priceUpdated = auctionDAO.updateCurrentPrice(auctionId, bidPrice);
        if (!priceUpdated) {
            System.out.println("Cảnh báo: Bid đã lưu nhưng không cập nhật được giá phiên!");
        }

        System.out.println("Đặt giá thành công! Giá mới: " + bidPrice);
        return true;
    }

    // ------------------------------------------------------------------ //
    //  XEM LỊCH SỬ                                                       //
    // ------------------------------------------------------------------ //

    /**
     * LẤY LỊCH SỬ THEO LONG (Hỗ trợ code cũ): 
     * Sửa lỗi Type Mismatch bằng cách chuyển đổi List<BidRecord> thành List<Map<String, Object>>
     */
    public List<Map<String, Object>> getBidHistory(long auctionId) {
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return List.of(); 

        String itemId = (String) auction.get("itemId");
        
        // Lấy danh sách Record từ DAO
        List<BidDAO.BidRecord> records = bidDAO.getBidHistory(itemId);
        
        // Khởi tạo danh sách Map để trả về tương thích với luồng cũ
        List<Map<String, Object>> legacyHistory = new ArrayList<>();
        for (BidDAO.BidRecord record : records) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", record.id());
            map.put("itemId", record.itemId());
            map.put("bidderId", record.bidderId());
            map.put("bidPrice", record.bidPrice());
            map.put("bidTime", record.bidTime());
            legacyHistory.add(map);
        }
        
        return legacyHistory;
    }

    /**
     * LẤY LỊCH SỬ THEO STRING (Hỗ trợ Controller/Service mới):
     * Trả về kiểu List<BidDAO.BidRecord> sạch dữ liệu đúng như các Controller mong đợi.
     */
    public List<BidDAO.BidRecord> getBidHistory(String productId) {
        if (productId == null || productId.isBlank()) {
            return List.of();
        }
        return bidDAO.getBidHistory(productId);
    }

    /**
     * Lấy giá cao nhất hiện tại của 1 phiên qua ID Số (long).
     */
    public double getHighestBid(long auctionId) {
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return 0;

        String itemId = (String) auction.get("itemId");
        return bidDAO.getHighestBid(itemId);
    }

    /**
     * Lấy giá cao nhất hiện tại của một phiên thông qua chuỗi productId (String).
     */
    public double getHighestBid(String productId) {
        if (productId == null || productId.isBlank()) return 0;
        return bidDAO.getHighestBid(productId);
    }
}