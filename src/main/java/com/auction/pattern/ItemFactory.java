package com.auction.pattern;

import com.auction.model.Entity.Item.Art;
import com.auction.model.Entity.Item.Electronics;
import com.auction.model.Entity.Item.Item;
import com.auction.model.Entity.Item.Vehicle;

public class ItemFactory {

    /**
     * Tạo Item theo loại
     *
     * @param type         "ART" | "ELECTRONICS" | "VEHICLE"
     * @param id           ID sản phẩm
     * @param name         Tên sản phẩm
     * @param startPrice   Giá khởi điểm
     * @param endTime      Thời gian kết thúc (VD: "2026-12-31 23:59:59")
     * @param sellerId     ID người bán
     * @param extra        Thông tin thêm: artist / warrantyPeriod / mileage
     */
    public static Item createItem(String type, String id, String name,
                                  double startPrice, String endTime,
                                  String sellerId, String extra) {
        if (type == null) throw new IllegalArgumentException("Type không được null");

        return switch (type.toUpperCase()) {
            case "ART" ->
                new Art(id, name, startPrice, endTime, sellerId, extra); // extra = tên họa sĩ

            case "ELECTRONICS" ->
                new Electronics(id, name, startPrice, endTime, sellerId,
                    parseIntSafe(extra, 12)); // extra = số tháng bảo hành

            case "VEHICLE" ->
                new Vehicle(id, name, startPrice, endTime, sellerId,
                    parseIntSafe(extra, 0)); // extra = số km

            default -> throw new IllegalArgumentException("Loại Item không hợp lệ: " + type);
        };
    }

    // Chuyển String → int an toàn, nếu lỗi trả về defaultValue
    private static int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}