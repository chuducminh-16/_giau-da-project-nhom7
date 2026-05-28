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
import java.time.LocalDateTime;
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
    /**
     * Giai ma da hinh (Polymorphic Deserialization) - trich xuat thu cong tu JsonObject
     * De tranh loi Gson khong the deserialize LocalDateTime.
     */
    public static Item deserializeItem(JsonObject obj) {
        try {
            String type = "ART";
            if (obj.has("type") && !obj.get("type").isJsonNull()) {
                type = obj.get("type").getAsString().toUpperCase();
            }

            // Trich xuat cac field co ban
            String id   = obj.has("id")   && !obj.get("id").isJsonNull()   ? obj.get("id").getAsString()   : null;
            String name  = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "";
            String sid   = obj.has("sellerId") && !obj.get("sellerId").isJsonNull() ? obj.get("sellerId").getAsString() : "";
            double sp    = obj.has("startingPrice") && !obj.get("startingPrice").isJsonNull() ? obj.get("startingPrice").getAsDouble() : 0;
            double cb    = obj.has("currentBid") && !obj.get("currentBid").isJsonNull() ? obj.get("currentBid").getAsDouble() : sp;
            double bi    = obj.has("bidIncrement") && !obj.get("bidIncrement").isJsonNull() ? obj.get("bidIncrement").getAsDouble() : 0;
            String desc  = obj.has("description") && !obj.get("description").isJsonNull() ? obj.get("description").getAsString() : "";
            String img   = obj.has("imagePath") && !obj.get("imagePath").isJsonNull() ? obj.get("imagePath").getAsString() : "";
            String stat  = obj.has("status") && !obj.get("status").isJsonNull() ? obj.get("status").getAsString() : "OPEN";

            // Parse LocalDateTime an toan (xu ly ca String va JsonObject do Gson mac dinh)
            LocalDateTime endTime = parseLocalDateTime(obj, "endTime");
            LocalDateTime startTime = parseLocalDateTime(obj, "startTime");
            // Tao doi tuong Item theo loai
            Item item = switch (type) {
                case "ELECTRONICS" -> new Electronics(id, name, sp, endTime, sid, 0);
                case "VEHICLE"     -> new Vehicle(id, name, sp, endTime, sid, 0);
                default            -> new Art(id, name, sp, endTime, sid, desc);
            };
            item.setType(type);
            item.setCurrentBid(cb > 0 ? cb : sp);
            item.setBidIncrement(bi);
            item.setDescription(desc);
            item.setStatus(stat);
            if (!img.isBlank()) item.setImagePath(img);
            if (startTime != null) item.setStartTime(startTime);
            return item;
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
    /** Phan hoi sau khi them san pham moi */
    private void handleAddProduct(Message msg) {
        try {
            JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
            if (root.has("success") && root.get("success").getAsBoolean()) {
                // Lay item tu response va them truc tiep vao bang thay vi goi loadMyProducts()
                // De tranh loi deserialize LocalDateTime khi reload tu server
                if (root.has("item") && !root.get("item").isJsonNull()) {
                    Item newItem = deserializeItem(root.getAsJsonObject("item"));
                    if (newItem != null) {
                        controller.getProductData().add(0, newItem);
                    }
                }
                controller.clearForm();
                // Van goi loadMyProducts de dam bao dong bo voi DB
                controller.loadMyProducts();
                controller.showStatus("Thêm sản phẩm thành công!", false);
            } else {
                controller.showStatus("? " + controller.safeMsg(root), true);
            }
        } catch (Exception e) {
            controller.showStatus("? Loi parse response: " + e.getMessage(), true);
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

    /**
     * Helper: Parse LocalDateTime an toan tu JsonObject.
     * Xu ly ca 2 truong hop: Gson serialize thanh String (LocalDateTime.toString())
     * hoac Gson serialize thanh JsonObject phuc tap (mac dinh).
     */
    private static LocalDateTime parseLocalDateTime(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return null;
        try {
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive()) {
                // Truong hop 1: da duoc serialize thanh String
                String s = el.getAsString().replace(" ", "T");
                return LocalDateTime.parse(s.substring(0, Math.min(s.length(), 19)));
            } else if (el.isJsonObject()) {
                // Truong hop 2: Gson mac dinh serialize LocalDateTime thanh {year, month, day, hour, ...}
                JsonObject dt = el.getAsJsonObject();
                // Gson serialize LocalDateTime thanh nested object voi cac field
                // { "date": { "year": 2026, "month": 5, "day": 29 }, "time": { "hour": 23, ... } }
                if (dt.has("date") && dt.has("time")) {
                    JsonObject date = dt.getAsJsonObject("date");
                    JsonObject time = dt.getAsJsonObject("time");
                    int year = date.has("year") ? date.get("year").getAsInt() : 2026;
                    int month = date.has("month") ? date.get("month").getAsInt() : 1;
                    int day = date.has("day") ? date.get("day").getAsInt() : 1;
                    int hour = time.has("hour") ? time.get("hour").getAsInt() : 0;
                    int minute = time.has("minute") ? time.get("minute").getAsInt() : 0;
                    int second = time.has("second") ? time.get("second").getAsInt() : 0;
                    return LocalDateTime.of(year, month, day, hour, minute, second);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
