package com.auction.server.network;

import com.auction.client.network.Message;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;

/**
 * ImageHandler - Thành phần chuyên biệt chịu trách nhiệm xử lý các tác vụ 
 * lưu trữ (Upload) và tải (Get) dữ liệu hình ảnh dưới dạng chuỗi mã hóa Base64.
 */
public class ImageHandler {

    private final Gson gson;

    public ImageHandler(Gson gson) {
        this.gson = gson;
    }

    /**
     * Xử lý giải mã chuỗi Base64 và lưu file ảnh vào thư mục cục bộ của Server
     * @param payload Chuỗi JSON chứa tên file gốc và dữ liệu ảnh Base64
     */
    public Message handleUploadImage(String payload) {
        try {
            // Phân tách dữ liệu thô từ chuỗi JSON nhận được
            JsonObject obj = gson.fromJson(payload, JsonObject.class);
            String fileName = obj.get("fileName").getAsString()
                                .replaceAll("[^a-zA-Z0-9._-]", "_"); // Sanitize loại bỏ ký tự lạ để tránh lỗi bảo mật file hệ thống
            String base64   = obj.get("data").getAsString();

            // Khởi tạo thư mục lưu trữ cục bộ nếu chưa tồn tại
            File dir = new File("uploads/images");
            dir.mkdirs();

            // Chèn thêm mốc thời gian System.currentTimeMillis() để đảm bảo tên file luôn là độc nhất không bị ghi đè
            String uniqueName = System.currentTimeMillis() + "_" + fileName;
            File outFile = new File(dir, uniqueName);

            // Tiến hành dịch chuỗi Base64 ngược lại thành mảng byte nhị phân dữ liệu ảnh gốc
            byte[] bytes = Base64.getDecoder().decode(base64);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(bytes); // Ghi mảng byte xuống đĩa cứng thành file ảnh hoàn chỉnh
            }

            // Phản hồi kết quả Upload thành công kèm theo đường dẫn file lưu trữ trên Server
            return new Message("UPLOAD_IMAGE_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "imagePath", "uploads/images/" + uniqueName
            )));

        } catch (Exception e) {
            e.printStackTrace();
            // Trả về gói thông báo thất bại nếu xảy ra lỗi luồng IO hoặc giải mã Base64
            return new Message("UPLOAD_IMAGE_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi upload: " + e.getMessage()
            )));
        }
    }

    /**
     * Xử lý đọc file ảnh từ đĩa cứng, chuyển đổi sang chuỗi Base64 để gửi về cho Client hiển thị
     * @param payload Chuỗi JSON chứa đường dẫn của ảnh muốn lấy
     */
    public Message handleGetImage(String payload) {
        try {
            JsonObject obj = gson.fromJson(payload, JsonObject.class);
            String imagePath = obj.get("imagePath").getAsString();

            // Lấy thông tin đường dẫn tuyệt đối chuẩn hóa (Canonical Path) để tăng tính bảo mật
            File file = new File(imagePath).getCanonicalFile();
            File base = new File("uploads").getCanonicalFile();
            
            // Lớp bảo vệ (Security Guard): Chặn đứng Path Traversal Attack (ngăn client đọc lén các file hệ thống nhạy cảm bằng mẹo "../")
            if (!file.toPath().startsWith(base.toPath()) || !file.exists()) {
                return new Message("GET_IMAGE_RESPONSE", gson.toJson(Map.of(
                        "success", false, "message", "Không tìm thấy ảnh.")));
            }

            // Đọc toàn bộ tệp tin hình ảnh lên thành mảng byte
            byte[] bytes  = Files.readAllBytes(file.toPath());
            // Mã hóa mảng byte dữ liệu thô sang chuỗi văn bản Base64 an toàn để dễ truyền tải qua giao thức Socket text-based
            String base64 = Base64.getEncoder().encodeToString(bytes);

            // Trả về phản hồi chứa đầy đủ chuỗi Base64 của ảnh cho Client nhận dạng hiển thị UI
            return new Message("GET_IMAGE_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "imagePath", imagePath,
                    "data",      base64
            )));

        } catch (Exception e) {
            return new Message("GET_IMAGE_RESPONSE", gson.toJson(Map.of(
                    "success", false, "message", "Lỗi: " + e.getMessage())));
        }
    }
}