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
 * ════════════════════════════════════════════════════════════════════════
 * 📡 PRODUCT MESSAGE HANDLER — Xử lý gói tin mạng cho màn hình Seller
 * ════════════════════════════════════════════════════════════════════════
 *
 * Quy ước màu thông báo (showStatus):
 *   🟢 isError = false (xanh lá) → Thêm mới, cập nhật thành công, upload ảnh
 *   🔴 isError = true  (đỏ)     → Xóa sản phẩm, lỗi, cảnh báo
 *
 * ════════════════════════════════════════════════════════════════════════
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
     * Giải mã đa hình (Polymorphic Deserialization) —
     * xác định đúng lớp con (Art / Electronics / Vehicle) dựa trên tag "type"
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
     * Phân phối gói tin từ Server về đúng hàm xử lý.
     * Luôn chạy trên JavaFX Application Thread qua Platform.runLater.
     */
    public void handleServerResponse(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {
                case "MY_PRODUCTS_RESPONSE"    -> handleMyProducts(msg);
                case "ADD_PRODUCT_RESPONSE"    -> handleAddProduct(msg);
                case "UPDATE_PRODUCT_RESPONSE" -> handleUpdateProduct(msg);
                case "DELETE_PRODUCT_RESPONSE" -> handleDeleteProduct(msg);
                case "UPLOAD_IMAGE_RESPONSE"   -> controller.getImageHandler().onUploadResponse(msg.getPayload());
                case "GET_IMAGE_RESPONSE"      -> controller.getImageHandler().onGetImageResponse(msg.getPayload());
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // Các hàm xử lý từng loại gói tin
    // ════════════════════════════════════════════════════════════════════

    /** Nhận danh sách sản phẩm của Seller và đổ vào TableView */
    private void handleMyProducts(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            JsonArray arr = null;

            // Tương thích nhiều tên field khác nhau từ các phiên bản Server
            if (root.has("products") && !root.get("products").isJsonNull())
                arr = root.getAsJsonArray("products");
            else if (root.has("items") && !root.get("items").isJsonNull())
                arr = root.getAsJsonArray("items");
            else if (root.has("data") && !root.get("data").isJsonNull())
                arr = root.getAsJsonArray("data");

            if (arr != null) {
                List<Item> items = new ArrayList<>();
                for (JsonElement el : arr) {
                    Item item = deserializeItem(el.getAsJsonObject());
                    if (item != null) items.add(item);
                }
                controller.getProductData().setAll(items);
                // 🟢 Tải danh sách thành công → xanh
                controller.showStatus("Tải " + items.size() + " sản phẩm thành công.", false);
            } else {
                // 🔴 Không tìm thấy danh sách → đỏ
                String errMsg = root.has("message") ? root.get("message").getAsString() : "Không tìm thấy sản phẩm.";
                controller.showStatus("⚠ " + errMsg, true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi xử lý dữ liệu từ Server!", true);
        }
    }

    /** Phản hồi sau khi thêm sản phẩm mới */
    private void handleAddProduct(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            if (root.has("success") && root.get("success").getAsBoolean()) {
                controller.clearForm();
                controller.loadMyProducts();
                // 🟢 Thêm thành công → xanh
                controller.showStatus("✓ Thêm sản phẩm thành công!", false);
            } else {
                // 🔴 Thất bại → đỏ
                controller.showStatus("⚠ " + controller.safeMsg(root), true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi parse response: " + e.getMessage(), true);
        }
    }

    /** Phản hồi sau khi cập nhật sản phẩm */
    private void handleUpdateProduct(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            if (root.has("success") && root.get("success").getAsBoolean()) {
                controller.loadMyProducts();
                // 🟢 Cập nhật thành công → xanh
                controller.showStatus("✓ Cập nhật sản phẩm thành công!", false);
            } else {
                // 🔴 Thất bại → đỏ
                controller.showStatus("⚠ " + controller.safeMsg(root), true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi: " + e.getMessage(), true);
        }
    }

    /** Phản hồi sau khi xóa sản phẩm */
    private void handleDeleteProduct(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            if (root.has("success") && root.get("success").getAsBoolean()) {
                Item selected = controller.getTableProducts().getSelectionModel().getSelectedItem();
                if (selected != null) controller.getProductData().remove(selected);
                controller.clearForm();
                // 🔴 Xóa thành công → vẫn dùng đỏ vì đây là thao tác xóa
                controller.showStatus("🗑 Đã xoá sản phẩm thành công!", true);
            } else {
                // 🔴 Thất bại → đỏ
                controller.showStatus("⚠ " + controller.safeMsg(root), true);
            }
        } catch (Exception e) {
            controller.showStatus("⚠ Lỗi: " + e.getMessage(), true);
        }
    }
}
