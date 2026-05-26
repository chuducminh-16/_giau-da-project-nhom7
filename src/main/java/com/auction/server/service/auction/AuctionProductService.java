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
import java.util.UUID;

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

        // Sinh mã ID sản phẩm ngẫu nhiên ngắn gọn
        String itemId   = "I-" + UUID.randomUUID().toString().substring(0, 8);
        String itemType = (type != null && !type.isBlank()) ? type.toUpperCase() : "ART";

        // Sử dụng Pattern Matching switch để khởi tạo đúng thực thể Model kế thừa tương ứng
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

        // Gán các thông số bổ trợ cho đối tượng sản phẩm đấu giá
        item.setBidIncrement(bidIncrement);
        item.setImagePath(imagePath);
        item.setStartTime(startTime);
        item.setStatus("OPEN");
        item.setDescription(description);

        // Lưu thông tin mô tả chi tiết sản phẩm xuống DB qua DAO
        boolean itemSaved = itemSaveDAO.saveItem(item);
        if (!itemSaved) {
            System.err.println("[AuctionProductService] saveItem that bai: " + name);
            return null;
        }

        // Tạo song song một phiên đấu giá tương thích liên kết trực tiếp với sản phẩm vừa tạo
        long auctionId = System.currentTimeMillis();
        boolean auctionSaved = auctionDAO.saveAuction(
                auctionId, itemId, sellerId, startPrice, endTime.toString());
        if (!auctionSaved) {
            System.err.println("[AuctionProductService] saveAuction that bai: " + itemId);
            return null;
        }

        System.out.printf("[AuctionProductService] Them san pham OK: id=%s name=%s type=%s price=%.0f%n",
                itemId, name, itemType, startPrice);
        return item;
    }

    /**
     * Hàm Overload thêm sản phẩm mặc định kiểu nghệ thuật (ART)
     */
    public Item addProduct(String sellerId, String name, String description,
                           double startPrice, double bidIncrement,
                           String imagePath, LocalDateTime startTime,
                           LocalDateTime endTime) {
        return addProduct(sellerId, name, description, startPrice, bidIncrement,
                imagePath, startTime, endTime, "ART");
    }

    /**
     * Cập nhật thông tin sản phẩm (Trường hợp có thay đổi cả ảnh mới)
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
     * Cập nhật thông tin sản phẩm (Trường hợp giữ nguyên đường dẫn ảnh cũ)
     */
    public boolean updateProduct(String productId, String name, String description,
                                 double startPrice, double bidIncrement,
                                 LocalDateTime endTime, String type) {
        return itemSaveDAO.updateItem(productId, name, description,
                startPrice, bidIncrement, endTime, type);
    }

    /**
     * Xóa hoàn toàn sản phẩm khỏi hệ thống
     */
    public boolean deleteProduct(String productId) {
        return itemSaveDAO.deleteItem(productId);
    }

    /**
     * Đóng và chấm dứt phiên đấu giá để thực hiện phân định thắng/thua
     */
    public Map<String, Object> endAuction(long auctionId) {
        Map<String, Object> auction = auctionDAO.findById(auctionId);
        if (auction == null) return null;

        String status = (String) auction.get("status");
        // Nếu phiên đã ở trạng thái kết thúc hoặc hủy từ trước thì bỏ qua không xử lý lại
        if ("FINISHED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status))
            return null;

        // Tìm kiếm người đặt mức giá cao nhất trong hệ thống cho phòng này
        Map<String, Object> winner = auctionDAO.findWinner(auctionId);

        String itemId = (String) auction.get("itemId");
        if (itemId == null) itemId = (String) auction.get("item_id");

        if (winner != null) {
            // Trường hợp tìm thấy người chiến thắng hợp lệ
            String winnerId   = (String) winner.get("bidderId");
            double finalPrice = ((Number) winner.get("finalPrice")).doubleValue();
            
            // Đúc kết thương vụ: Ghi nhận thông tin hóa đơn hóa đơn vào bảng Transactions
            transactionDAO.saveTransaction(itemId, winnerId, finalPrice);
            // Chuyển trạng thái phiên đấu giá sang hoàn thành FINISHED
            auctionDAO.updateStatus(auctionId, "FINISHED");
            winner.put("itemId", itemId);
            return winner;
        } else {
            // Không có bất kỳ ai tham gia đặt giá -> Đổi trạng thái phiên đấu giá thành hủy CANCELED
            auctionDAO.updateStatus(auctionId, "CANCELED");
            return null;
        }
    }
}