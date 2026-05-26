package com.auction.client.utils;

import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonParseException;

import java.lang.reflect.Type;

/**
 * 🛠️ BỘ CHUYỂN ĐỔI DỮ LIỆU ĐA HÌNH SẢN PHẨM (GSON ITEM TYPE ADAPTER)
 * - Nhiệm vụ: Tách biệt logic phân tích JSON của tầng dữ liệu ra khỏi Controller.
 * - Cách hoạt động: Đọc thuộc tính "type" trong chuỗi JSON nhận được từ Server, 
 * tự động ánh xạ và ép về đúng lớp con tương ứng (Electronics, Vehicle, Art).
 */
public class ItemTypeAdapter implements JsonDeserializer<Item> {

    @Override
    public Item deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx) throws JsonParseException {
        // Chuyển đổi phần tử gốc JSON về dạng đối tượng Key-Value (JsonObject)
        JsonObject obj = json.getAsJsonObject();

        // Khởi tạo phân loại mặc định là tác phẩm nghệ thuật (ART)
        String type = "ART"; 
        
        // Kiểm tra xem chuỗi JSON từ Server gửi về có trường "type" và không bị rỗng (null) hay không
        if (obj.has("type") && !obj.get("type").isJsonNull()) {
            type = obj.get("type").getAsString().toUpperCase();
        }

        // Dựa vào chuỗi phân loại, ra lệnh cho Gson tự động bóc tách các trường đặc thù của Class con đó
        return switch (type) {
            case "ELECTRONICS" -> ctx.deserialize(obj, Electronics.class);
            case "VEHICLE"     -> ctx.deserialize(obj, Vehicle.class);
            default            -> ctx.deserialize(obj, Art.class); // Mặc định trả về Art nếu không khớp
        };
    }
}