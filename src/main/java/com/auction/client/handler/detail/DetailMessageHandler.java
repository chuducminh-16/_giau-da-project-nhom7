package com.auction.client.handler.detail;

import com.auction.client.controller.DetailController;
import com.auction.client.network.Message;
import com.auction.client.utils.ItemTypeAdapter;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import javafx.application.Platform;

/**
 * 📡 BỘ LẮNG NGHE VÀ XỬ LÝ GÓI TIN CHI TIẾT (DETAIL MESSAGE HANDLER)
 * - Nhiệm vụ: Xử lý toàn bộ dữ liệu thô nhận từ mạng, kiểm tra tính hợp lệ và cập nhật trạng thái.
 */
public class DetailMessageHandler {

    private final DetailController controller;
    private final Gson gson;

    public DetailMessageHandler(DetailController controller) {
        this.controller = controller;
        // Đăng ký sử dụng ItemTypeAdapter mới tách để tạo ra bộ Gson đa hình
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Item.class, new ItemTypeAdapter())
                .create();
    }

    /**
     * Hàm xử lý trung tâm đón nhận các gói tin từ Server đẩy về máy khách.
     */
    public void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            // 📦 TRƯỜNG HỢP 1: Server trả về toàn bộ cấu trúc dữ liệu của sản phẩm
            case "PRODUCT_DETAIL_RESPONSE" -> {
                try {
                    JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                    // Nếu Server báo thất bại (ví dụ: Sản phẩm đã bị xóa hoặc khóa)
                    if (!root.has("success") || !root.get("success").getAsBoolean()) {
                        Platform.runLater(() -> controller.updateProductNameLabel("Không tìm thấy sản phẩm."));
                        return;
                    }

                    // Trích xuất cây thư mục con mang tên "item" trong gói JSON
                    JsonObject itemObj = root.getAsJsonObject("item");

                    String type = "ART";
                    if (itemObj.has("type") && !itemObj.get("type").isJsonNull()) {
                        type = itemObj.get("type").getAsString().toUpperCase();
                    }

                    // Tiến hành Deserialize đa hình thông qua bộ cấu hình Gson
                    Item item;
                    switch (type) {
                        case "ELECTRONICS" -> item = gson.fromJson(itemObj, Electronics.class);
                        case "VEHICLE"     -> item = gson.fromJson(itemObj, Vehicle.class);
                        default            -> item = gson.fromJson(itemObj, Art.class);
                    }

                    if (item != null) {
                        item.setType(type); // Gán chuỗi danh mục chuẩn hóa
                        
                        // Đầy dữ liệu sạch sang luồng hiển thị của JavaFX để cập nhật giao diện (UI)
                        Platform.runLater(() -> {
                            controller.setCurrentItem(item);
                            controller.setWatchingItemId(item.getId());
                            controller.populateUI(item); // Ra lệnh cho Controller vẽ dữ liệu lên màn hình và kích hoạt đếm ngược
                        });
                    }
                } catch (Exception e) {
                    System.err.println("[DetailHandler] Lỗi bóc gói PRODUCT_DETAIL_RESPONSE: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // 💰 TRƯỜNG HỢP 2: Có người đấu giá khác vừa nâng giá sản phẩm thành công (Real-time update)
            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();
                    
                    // Kiểm tra chéo: Nếu gói tin cập nhật giá không khớp với ID sản phẩm đang xem thì bỏ qua
                    if (controller.getWatchingItemId() == null || !controller.getWatchingItemId().equals(productId)) return;

                    double newBid = dto.get("newBid").getAsDouble();
                    
                    // Đồng bộ giá mới lên nhãn hiển thị và cập nhật vào thuộc tính của đối tượng trong bộ nhớ
                    Platform.runLater(() -> {
                        if (controller.getCurrentPriceLabel() != null) {
                            controller.getCurrentPriceLabel().setText(String.format("Current Bid: %,.0f VNĐ", newBid));
                        }
                        if (controller.getCurrentItem() != null) {
                            controller.getCurrentItem().setCurrentBid(newBid);
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[DetailHandler] Lỗi phân tích cú pháp gói BID_UPDATE: " + e.getMessage());
                }
            }

            // 🛑 TRƯỜNG HỢP 3: Hết giờ đếm ngược, Server phát lệnh đóng phiên đấu giá toàn hệ thống
            case "AUCTION_ENDED" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String endedItemId = dto.has("itemId") && !dto.get("itemId").isJsonNull()
                            ? dto.get("itemId").getAsString() : null;
                    
                    // Nếu phiên kết thúc chính là phiên người dùng đang nhìn chằm chằm vào xem chi tiết
                    if (endedItemId != null && endedItemId.equals(controller.getWatchingItemId())) {
                        Platform.runLater(() -> {
                            if (controller.getLblStatus() != null) {
                                controller.getLblStatus().setText("FINISHED"); // Đổi nhãn trạng thái thành Đã kết thúc
                            }
                            // 🎯 Ép giao diện hiển thị trạng thái hết giờ ngay lập tức trên nhãn thời gian
                            // (Mặc dù đồng hồ Timeline của Controller sẽ tự phát hiện ra khi lệch giây, 
                            // việc cập nhật chủ động này giúp đồng bộ tức thời với tín hiệu Server)
                        });
                    }
                } catch (Exception e) {
                    System.err.println("[DetailHandler] Lỗi phân tích cú pháp gói AUCTION_ENDED: " + e.getMessage());
                }
            }

            // 🖼️ TRƯỜNG HỢP 4: Nhận mảng dữ liệu byte của hình ảnh truyền về từ Server tệp tin
            case "GET_IMAGE_RESPONSE" -> controller.getImageHandler().onGetImageResponse(msg.getPayload());
        }
    }

    /** Trả ra bộ Gson đa hình dùng chung nếu Controller cần sử dụng */
    public Gson getGson() { return this.gson; }
}