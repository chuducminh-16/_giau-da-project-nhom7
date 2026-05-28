package com.auction.server.service.auction;

import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.item.ItemSaveDAO;
import com.auction.server.dao.transaction.TransactionDAO;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AuctionProductService - Chịu trách nhiệm quản lý vòng đời sản phẩm,
 * cấu hình các thuộc tính sản phẩm và giải quyết kết quả khi kết thúc phiên.
 */
public class AuctionProductService {

    private final AuctionDAO     auctionDAO     = new AuctionDAO();
    private final ItemSaveDAO    itemSaveDAO    = new ItemSaveDAO();
    private final TransactionDAO transactionDAO = new TransactionDAO();

    /**
     * Tạo mới sản phẩm đấu giá (Đầy đủ tham số chỉ định phân loại Type cụ thể)
     */
    public Item addProduct(String sellerId, String name, String description,
                           double startPrice, double bidIncrement,
                           String imagePath, LocalDateTime startTime,
                           LocalDateTime endTime, String type) {

        // Sinh ID sản phẩm tuần tự → "SP-001", "SP-002"...
        String itemId   = itemSaveDAO.getNextItemId();
        String itemType = (type != null && !type.isBlank()) ? type.toUpperCase() : "ART";

        // Khởi tạo đúng thực thể Model theo loại sản phẩm
        Item item = switch (itemType) {
            case "ELECTRONICS" -> {
                Electronics e = new Electronics(itemId, name, startPrice, endTime, sellerId, 0);
                e.setDescription(description);
                yield e;
            }
            case "VEHICLE" -> {
                Vehicle v = new Vehicle(itemId, name, startPrice, endTime, sellerId, 0);
                v.setDescription(description);
                yield v;
            }
            default -> new Art(itemId, name, startPrice, endTime, sellerId, description);
        };

        // Gán các thông số bổ trợ
        item.setBidIncrement(bidIncrement);
        item.setImagePath(imagePath);
        item.setStartTime(startTime);
        item.setStatus("OPEN");
        item.setDescription(description);

        // Lưu sản phẩm xuống DB
        boolean itemSaved = itemSaveDAO.saveItem(item);
        if (!itemSaved) {
            System.err.println("[AuctionProductService] saveItem that bai: " + name);
            return null;
        }

        // Sinh ID phiên đấu giá tuần tự → lấy từ DB (AUTO_INCREMENT)
        // Dùng auctionDAO để lấy ID tiếp theo thay vì System.currentTimeMillis()
        long auctionId = auctionDAO.getNextAuctionId();
        boolean auctionSaved = auctionDAO.saveAuction(
                auctionId, itemId, sellerId, startPrice, endTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        if (!auctionSaved) {
            System.err.println("[AuctionProductService] saveAuction that bai: " + itemId);
            return null;
        }

        System.out.printf("[AuctionProductService] Them san pham OK: id=%s name=%s type=%s price=%.0f%n",
                itemId, name, itemType, startPrice);
        return item;
    }

    /**
     * Overload thêm sản phẩm mặc định kiểu ART
     */
    public Item addProduct(String sellerId, String name, String description,
                           double startPrice, double bidIncrement,
                           String imagePath, LocalDateTime startTime,
                           LocalDateTime endTime) {
        return addProduct(sellerId, name, description, startPrice, bidIncrement,
                imagePath, startTime, endTime, "ART");
    }

    /**
     * Cập nhật thông tin sản phẩm (có ảnh mới)
     */
    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime, String imagePath, String type) {
        if (imagePath != null && !imagePath.isBlank()) {
            return itemSaveDAO.updateItem(productId, name, description,
                    startPrice, bidIncrement, endTime, imagePath, type);
        } else {
            return itemSaveDAO.updateItem(productId, name, description,
                    startPrice, bidIncrement, endTime, type);
        }
    }

    /**
     * Cập nhật thông tin sản phẩm (giữ ảnh cũ)
     */
    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime, String type) {
        return itemSaveDAO.updateItem(productId, name, description,
                startPrice, bidIncrement, endTime, type);
    }

    /**
     * Xóa sản phẩm khỏi hệ thống
     */
    public boolean deleteProduct(String productId) {
        return itemSaveDAO.deleteItem(productId);
    }

    /**
     * Đóng phiên đấu giá và phân định thắng/thua
     */
    public Map<String, Object> endAuction(long auctionId) {
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return null;

        String status = (String) auction.get("status");
        if ("FINISHED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status))
            return null;

        Map<String, Object> winner = auctionDAO.findWinner(auctionId);

        String itemId = (String) auction.get("itemId");
        if (itemId == null) itemId = (String) auction.get("item_id");

        if (winner != null) {
            String winnerId   = (String) winner.get("bidderId");
            double finalPrice = ((Number) winner.get("finalPrice")).doubleValue();
            transactionDAO.saveTransaction(itemId, winnerId, finalPrice);
            auctionDAO.updateStatus(auctionId, "FINISHED");
            winner.put("itemId", itemId);
            return winner;
        } else {
            auctionDAO.updateStatus(auctionId, "CANCELED");
            return null;
        }
    }
}