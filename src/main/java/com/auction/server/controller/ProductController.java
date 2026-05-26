package com.auction.server.controller;

import com.auction.client.network.Message; 
import com.auction.server.service.auction.AuctionProductService;
import com.auction.server.service.auction.AuctionQueryService;
import com.auction.server.dao.item.ItemFindDAO;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 📦 PRODUCT CONTROLLER (BỘ ĐIỀU PHỐI QUẢN LÝ SẢN PHẨM)
 * - Vị trí thư mục: com.auction.server.controller
 * - Trách nhiệm: Tiếp nhận và xử lý toàn bộ các yêu cầu mang tính chất CRUD tĩnh độc lập
 * (Xem danh sách, thêm, sửa, xóa, xem chi tiết) từ tài khoản Seller hoặc Admin.
 * - Đặc điểm: Hoạt động theo mô hình API Request-Response (Không duy trì trạng thái luồng).
 */
public class ProductController {

    private final AuctionProductService auctionProductService = new AuctionProductService();
    private final AuctionQueryService auctionQueryService = new AuctionQueryService();
    private final Gson gson = new Gson();

    /**
     * 📥 XEM DANH SÁCH SẢN PHẨM CỦA RIÊNG MỘT SELLER
     * Payload yêu cầu chứa: { "sellerId": "Mã định danh người bán" }
     */
    public Message handleGetMyProducts(String payload) {
        try {
            // 1. Phân rã JSON thành đối tượng DTO để lấy sellerId
            GetMyProductsDto dto = gson.fromJson(payload, GetMyProductsDto.class);
            
            // 2. Gọi tầng Query Service để thực thi câu lệnh đọc (SELECT) dữ liệu từ DB
            List<Item> items = auctionQueryService.getProductsBySeller(dto.sellerId());
            
            // 3. Trả về gói tin phản hồi kèm theo danh sách sản phẩm tìm được
            return new Message("MY_PRODUCTS_RESPONSE", gson.toJson(Map.of("items", items)));
        } catch (Exception e) {
            return error("Không thể tải sản phẩm của bạn: " + e.getMessage());
        }
    }

    /**
     * ➕ ĐĂNG TẢI MỚI MỘT SẢN PHẨM LÊN SÀN
     * Quyền hạn bắt buộc: Phải là SELLER hoặc ADMIN mới được thực thi
     */
    public Message handleAddProduct(String payload, String role) {
        // 1. Kiểm tra phân quyền an toàn tầng biên (Edge Validation)
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Chỉ tài khoản Người bán (Seller) mới có quyền thêm sản phẩm.");
        }
        try {
            // 2. Chuyển đổi chuỗi dữ liệu JSON nhận được sang DTO cấu trúc
            AddProductDto dto = gson.fromJson(payload, AddProductDto.class);

            // 3. Kiểm tra tính hợp lệ của dữ liệu (Data Validation) tránh dữ liệu rác
            if (dto.name() == null || dto.name().isBlank()) return error("Tên sản phẩm không được trống.");
            if (dto.startPrice() <= 0) return error("Giá khởi điểm phải lớn hơn 0 VNĐ.");
            if (dto.endTime() == null || dto.endTime().isBlank()) return error("Thời gian kết thúc không được trống.");

            // 4. Ép kiểu mốc thời gian kết thúc (thay ký tự khoảng trắng sang chữ T theo ISO-8601)
            LocalDateTime endTime;
            try {
                endTime = LocalDateTime.parse(dto.endTime().replace(" ", "T"));
            } catch (Exception e) {
                return error("Định dạng mốc thời gian kết thúc không hợp lệ.");
            }
            if (endTime.isBefore(LocalDateTime.now())) return error("Thời gian kết thúc không được nằm trong quá khứ.");

            // 5. Cấu hình mốc thời gian bắt đầu. Nếu client bỏ trống thì mặc định lấy thời gian hiện tại
            LocalDateTime startTime = (dto.startTime() != null && !dto.startTime().isBlank())
                    ? LocalDateTime.parse(dto.startTime().replace(" ", "T"))
                    : LocalDateTime.now();

            // 6. Ủy quyền lệnh ghi cho AuctionProductService xử lý Business Logic và lưu trữ Database
            Item item = auctionProductService.addProduct(
                    dto.sellerId(), dto.name(),
                    dto.description() != null ? dto.description() : "",
                    dto.startPrice(),
                    dto.bidIncrement() > 0 ? dto.bidIncrement() : 1000.0,
                    dto.imagePath() != null ? dto.imagePath() : "",
                    startTime, endTime,
                    dto.type() != null ? dto.type() : "ART"
            );

            if (item == null) return error("Không thể đẩy sản phẩm lên DB. Vui lòng thử lại.");

            // 7. Gửi trả thực thể sản phẩm vừa tạo thành công về Client để hiển thị giao diện mới
            return new Message("ADD_PRODUCT_RESPONSE", gson.toJson(Map.of("success", true, "item", item)));
        } catch (Exception e) {
            e.printStackTrace();
            return error("Lỗi hệ thống khi đăng sản phẩm: " + e.getMessage());
        }
    }

    /**
     * ✏️ CẬP NHẬT THÔNG TIN CHỈNH SỬA SẢN PHẨM
     */
    public Message handleUpdateProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Bạn không có quyền hạn chỉnh sửa sản phẩm này.");
        }
        try {
            UpdateProductDto dto = gson.fromJson(payload, UpdateProductDto.class);
            
            // Thực thi cập nhật thông qua tầng Service chuyên trách ghi
            boolean ok = auctionProductService.updateProduct(
                    dto.productId(), dto.name(), dto.description(),
                    dto.startPrice(), dto.bidIncrement(),
                    LocalDateTime.parse(dto.endTime().replace(" ", "T")),
                    dto.imagePath(),
                    dto.type());

            return new Message("UPDATE_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", ok, "message", ok ? "Cập nhật thành công!" : "Cập nhật thất bại.")));
        } catch (Exception e) {
            return error("Lỗi cập nhật sản phẩm: " + e.getMessage());
        }
    }

    /**
     * ❌ XÓA SẢN PHẨM RA KHỎI SÀN KINH DOANH
     */
    public Message handleDeleteProduct(String payload, String role) {
        if (!"SELLER".equalsIgnoreCase(role) && !"ADMIN".equalsIgnoreCase(role)) {
            return error("Bạn không có quyền gỡ bỏ sản phẩm này.");
        }
        try {
            DeleteProductDto dto = gson.fromJson(payload, DeleteProductDto.class);
            
            // Thực hiện xóa mềm hoặc xóa cứng thông qua Service chuyên biệt
            boolean ok = auctionProductService.deleteProduct(dto.productId());
            return new Message("DELETE_PRODUCT_RESPONSE",
                    gson.toJson(Map.of("success", ok, "message", ok ? "Đã gỡ sản phẩm thành công." : "Không thể gỡ sản phẩm.")));
        } catch (Exception e) {
            return error("Lỗi xóa sản phẩm: " + e.getMessage());
        }
    }

    /**
     * 🔍 XEM CHI TIẾT THÔNG TIN MỘT SẢN PHẨM PHỤC VỤ TRANG PHÒNG ĐẤU GIÁ
     */
    public Message handleGetProductDetail(String payload) {
        try {
            GetProductDetailDto dto = gson.fromJson(payload, GetProductDetailDto.class);
            ItemFindDAO itemFindDAO = new ItemFindDAO();
            Item item = itemFindDAO.findById(dto.itemId());

            if (item == null) return error("Không tìm thấy thông tin chi tiết của sản phẩm này.");

            // Ép thêm thuộc tính 'type' tường minh vào JSON Tree để Client dễ nhận diện đa hình (Art, Vehicle, Electronics)
            JsonObject itemJson = gson.toJsonTree(item).getAsJsonObject();
            itemJson.addProperty("type", item.getType());

            return new Message("PRODUCT_DETAIL_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "item", itemJson
            )));
        } catch (Exception e) {
            return error("Lỗi tải chi tiết sản phẩm: " + e.getMessage());
        }
    }

    /** Hàm bổ trợ phản hồi lỗi nhanh dạng JSON */
    private Message error(String msg) {
        return new Message("ERROR", gson.toJson(Map.of("message", msg)));
    }

    // ── CÁC ĐỐI TƯỢNG DTO NHẬN PAYLOAD NỘI BỘ ──────────────────────────
    private record GetMyProductsDto(String sellerId) {}
    private record GetProductDetailDto(String itemId) {}
    private record DeleteProductDto(String productId) {}
    private record AddProductDto(String sellerId, String name, String description, double startPrice, double bidIncrement, String imagePath, String startTime, String endTime, String type) {}
    private record UpdateProductDto(String productId, String name, String description, double startPrice, double bidIncrement, String endTime, String imagePath, String type) {}
}