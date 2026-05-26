package com.auction.client.handler.product;

import com.auction.client.controller.ManageProductController;
import com.auction.client.network.Message;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.*;
import javafx.application.Platform;
import java.util.ArrayList;
import java.util.List;

/**
 * 📡 BỘ ĐIỀU PHỐI TIN NHẮN QUẢN LÝ SẢN PHẨM (MESSAGE HANDLER)
 * - Mục đích: Thu gọn toàn bộ cơ chế chuyển hóa Gson đa hình và bắt gói tin phản hồi của Server.
 */
public class ProductMessageHandler {

    private final ManageProductController controller;
    private final Gson gson;

    public ProductMessageHandler(ManageProductController controller) {
        this.controller = controller;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(Item.class, (JsonDeserializer<Item>) (json, typeOfT, ctx) -> 
                        deserializeItem(json.getAsJsonObject()))
                .create();
    }

    public Gson getGson() { return this.gson; }

    /**
     * Giải mã đa hình (Polymorphic Deserialization) xác định chính xác thực thể con dựa trên tag loại sản phẩm
     */
    public static Item deserializeItem(JsonObject obj) {
        try {
            String type = "ART";
            if (obj.has("type") && !obj.get("type").isJsonNull()) {
                type = obj.get("type").getAsString().toUpperCase();
            }
            Gson plain = new Gson();
            return switch (type) {
                case "ELECTRONICS" -> plain.fromJson(obj, Electronics.class);
                case "VEHICLE"     -> plain.fromJson(obj, Vehicle.class);
                default            -> plain.fromJson(obj, Art.class);
            };
        } catch (Exception e) { 
            return null; 
        }
    }

    /**
     * Phân phối xử lý bất đồng bộ từ luồng mạng Server gửi về UI
     */
    public void handleServerResponse(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {
                case "MY_PRODUCTS_RESPONSE"     -> handleMyProducts(msg);
                case "ADD_PRODUCT_RESPONSE"     -> handleAddProduct(msg);
                case "UPDATE_PRODUCT_RESPONSE"  -> handleUpdateProduct(msg);
                case "DELETE_PRODUCT_RESPONSE"  -> handleDeleteProduct(msg);
                case "UPLOAD_IMAGE_RESPONSE"    -> controller.getImageHandler().onUploadResponse(msg.getPayload());
                case "GET_IMAGE_RESPONSE"       -> controller.getImageHandler().onGetImageResponse(msg.getPayload());
            }
        });
    }

    private void handleMyProducts(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            JsonArray arr = null;
            // Khống chế lỗi sai lệch đặt tên biến danh sách giữa các phiên bản Server cũ/mới
            if (root.has("products") && !root.get("products").isJsonNull()) arr = root.getAsJsonArray("products");
            else if (root.has("items") && !root.get("items").isJsonNull())   arr = root.getAsJsonArray("items");
            else if (root.has("data") && !root.get("data").isJsonNull())     arr = root.getAsJsonArray("data");

            if (arr != null) {
                List<Item> items = new ArrayList<>();
                for (JsonElement el : arr) {
                    Item item = deserializeItem(el.getAsJsonObject());
                    if (item != null) items.add(item);
                }
                controller.getProductData().setAll(items); // Cập nhật đồng bộ vào TableView
                controller.showStatus("Tải " + items.size() + " sản phẩm thành công.", false);
            } else {
                controller.showStatus("⚠ " + (root.has("message") ? root.get("message").getAsString() : "Không tìm thấy sản phẩm."), true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi xử lý dữ liệu từ Server!", true);
        }
    }

    private void handleAddProduct(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            if (root.has("success") && root.get("success").getAsBoolean()) {
                controller.clearForm();
                controller.showStatus("✓ Thêm sản phẩm thành công!", false);
                controller.loadMyProducts(); // Tải lại danh sách mới
            } else {
                controller.showStatus("⚠ " + controller.safeMsg(root), true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi parse response: " + e.getMessage(), true);
        }
    }

    private void handleUpdateProduct(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            if (root.has("success") && root.get("success").getAsBoolean()) {
                controller.loadMyProducts();
                controller.showStatus("✓ Cập nhật thành công!", false);
            } else {
                controller.showStatus("⚠ " + controller.safeMsg(root), true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi: " + e.getMessage(), true);
        }
    }

    private void handleDeleteProduct(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            if (root.has("success") && root.get("success").getAsBoolean()) {
                Item selected = controller.getTableProducts().getSelectionModel().getSelectedItem();
                if (selected != null) controller.getProductData().remove(selected);
                controller.clearForm();
                controller.showStatus("✓ Đã xoá sản phẩm!", false);
            } else {
                controller.showStatus("⚠ " + controller.safeMsg(root), true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi: " + e.getMessage(), true);
        }
    }
}