package com.auction.client.handler.home;

import com.auction.client.controller.HomeController;
import com.auction.client.network.Message;
import com.auction.client.utils.ItemTypeAdapter;
import com.auction.client.utils.ToastNotification;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * 📡 BỘ XỬ LÝ GÓI TIN TRANG CHỦ (HOME MESSAGE HANDLER)
 * - Nhiệm vụ: Lắng nghe danh sách phòng đấu giá, bắt gói nâng giá từ user khác và bắn Toast thông báo.
 */
public class HomeMessageHandler {

    private final HomeController controller;
    private final Gson gson;

    public HomeMessageHandler(HomeController controller) {
        this.controller = controller;
        // Tái sử dụng ItemTypeAdapter đa hình tụi mình đã bóc tách từ trước
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Item.class, new ItemTypeAdapter())
                .create();
    }

    /** Xử lý phân tích gói dữ liệu mạng mạng từ Server */
    public void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            // 📋 NHẬN DANH SÁCH TOÀN BỘ PHÒNG ĐẤU GIÁ TRÊN HỆ THỐNG
            case "AUCTIONS_LIST" -> {
                try {
                    JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                    if (!root.has("auctions") || !root.get("auctions").isJsonArray()) return;

                    JsonArray arr = root.getAsJsonArray("auctions");
                    List<Item> items = new ArrayList<>();
                    for (JsonElement el : arr) {
                        Item item = deserializeItem(el.getAsJsonObject());
                        if (item != null) items.add(item);
                    }

                    // Đồng bộ đổ danh sách sạch vào bảng TableView trên luồng JavaFX
                    Platform.runLater(() -> {
                        controller.getAuctionList().setAll(items);
                        if (!items.isEmpty()) {
                            controller.updateHeroCard(items.get(0)); // Mặc định chọn phòng đầu tiên làm banner chính
                        }
                        System.out.println("[HomeHandler] Tải thành công " + items.size() + " phiên đấu giá.");
                    });
                } catch (Exception e) {
                    System.err.println("[HomeHandler] Lỗi bóc gói danh sách AUCTIONS_LIST: " + e.getMessage());
                }
            }

            // 💰 AI ĐÓ VỪA ĐẤU GIÁ THÀNH CÔNG (CẬP NHẬT GIÁ REAL-TIME TOÀN DÂN)
            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();
                    double newBid    = dto.get("newBid").getAsDouble();
                    String bidder    = dto.has("bidderName") ? dto.get("bidderName").getAsString() : "Ai đó";

                    Platform.runLater(() -> {
                        List<Item> currentList = controller.getAuctionList();
                        for (int i = 0; i < currentList.size(); i++) {
                            Item item = currentList.get(i);
                            if (item.getId().equals(productId)) {
                                item.setCurrentBid(newBid);
                                item.setStatus("RUNNING");
                                
                                // Đè đứt vị trí cũ để kích hoạt ObservableList re-render giá trị mới lên UI bảng
                                currentList.set(i, item);

                                // Nếu sản phẩm được nâng giá trùng với sản phẩm đang được hiển thị ở Banner lớn
                                if (controller.getHeroItem() != null && controller.getHeroItem().getId().equals(productId)) {
                                    controller.updateHeroCard(item);
                                }

                                // 🔥 BẮN TOAST THÔNG BÁO POPUP SINK NỔI LÊN GIAO DIỆN MÀN HÌNH
                                try {
                                    Stage stage = (Stage) controller.getAuctionTable().getScene().getWindow();
                                    ToastNotification.bid(stage, bidder, item.getName(), newBid);
                                } catch (Exception ex) {
                                    System.err.println("[HomeHandler] Lỗi hiển thị Toast: " + ex.getMessage());
                                }
                                break;
                            }
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[HomeHandler] Lỗi parse gói tin cập nhật BID_UPDATE: " + e.getMessage());
                }
            }
        }
    }

    /** Hàm tiện ích giải mã chuỗi JSON lồng sang đối tượng con kế thừa Item */
    private Item deserializeItem(JsonObject obj) {
        try {
            String type = obj.has("type") && !obj.get("type").isJsonNull()
                    ? obj.get("type").getAsString().toUpperCase() : "ART";
            return switch (type) {
                case "ELECTRONICS" -> gson.fromJson(obj, Electronics.class);
                case "VEHICLE"     -> gson.fromJson(obj, Vehicle.class);
                default            -> gson.fromJson(obj, Art.class);
            };
        } catch (Exception e) {
            System.err.println("[HomeHandler] Lỗi phân tích cú pháp sản phẩm cụ thể: " + e.getMessage());
            return null;
        }
    }
}