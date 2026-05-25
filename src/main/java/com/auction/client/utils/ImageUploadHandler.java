package com.auction.client.utils;

import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.google.gson.Gson;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Helper xử lý toàn bộ việc upload và load ảnh từ server.
 *
 * Cách dùng trong controller:
 *
 *   // 1. Khởi tạo (trong initialize())
 *   imageHandler = new ImageUploadHandler(client, imgPreview1);
 *
 *   // 2. Nút chọn ảnh
 *   imageHandler.pickAndUpload(window, path -> currentImagePath = path);
 *
 *   // 3. Load ảnh khi chọn row (truyền server path)
 *   imageHandler.loadFromServer(item.getImagePath());
 *
 *   // 4. Trong handleServerResponse switch:
 *   case "UPLOAD_IMAGE_RESPONSE" -> imageHandler.onUploadResponse(msg.getPayload());
 *   case "GET_IMAGE_RESPONSE"    -> imageHandler.onGetImageResponse(msg.getPayload());
 */
public class ImageUploadHandler {

    // Cache dùng chung giữa tất cả màn hình trong phiên
    private static final java.util.Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private final NetworkClient client;
    private final ImageView     previewView;
    private final Gson          gson = new Gson();

    // Callback báo lại serverPath sau khi upload thành công
    private Consumer<String> onUploadSuccess;

    // Callback báo lỗi (tùy chọn)
    private Consumer<String> onError;

    // Path hiện tại đang hiển thị — để map GET_IMAGE_RESPONSE đúng
    private String currentRequestPath = "";

    public ImageUploadHandler(NetworkClient client, ImageView previewView) {
        this.client      = client;
        this.previewView = previewView;
    }

    /** Đặt callback nhận serverPath khi upload xong. */
    public ImageUploadHandler onSuccess(Consumer<String> callback) {
        this.onUploadSuccess = callback;
        return this;
    }

    /** Đặt callback nhận thông báo lỗi. */
    public ImageUploadHandler onError(Consumer<String> callback) {
        this.onError = callback;
        return this;
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Mở FileChooser, nén ảnh, upload lên server.
     * Gọi trong @FXML button handler.
     *
     * @param window  cửa sổ cha để hiển thị dialog
     */
    public void pickAndUpload(Window window) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả ảnh",
                        "*.png", "*.jpg", "*.jpeg", "*.jfif", "*.webp", "*.bmp", "*.gif"),
                new FileChooser.ExtensionFilter("JPG / JPEG / JFIF",
                        "*.jpg", "*.jpeg", "*.jfif"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("WebP / BMP / GIF",
                        "*.webp", "*.bmp", "*.gif")
        );

        File file = chooser.showOpenDialog(window);
        if (file == null) return;

        // Preview local ngay lập tức
        setPreviewFromFile(file);

        // Nén + upload trong background
        new Thread(() -> {
            try {
                String base64  = ImageUtils.compressToBase64(file);
                String outName = file.getName().replaceAll("\\.[^.]+$", "") + ".jpg";
                String payload = gson.toJson(Map.of("fileName", outName, "data", base64));
                client.send(new Message("UPLOAD_IMAGE", payload));
            } catch (Exception e) {
                fireError("Lỗi nén ảnh: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Load ảnh từ server path (khi chọn row trong table hoặc mở màn hình detail).
     * Nếu đã cache thì hiển thị ngay, không request server.
     *
     * @param serverPath  đường dẫn server, ví dụ "uploads/images/xxx.jpg"
     */
    public void loadFromServer(String serverPath) {
        if (serverPath == null || serverPath.isBlank()) {
            if (previewView != null) previewView.setImage(null);
            return;
        }

        currentRequestPath = serverPath;

        // Kiểm tra cache
        Image cached = IMAGE_CACHE.get(serverPath);
        if (cached != null) {
            Platform.runLater(() -> setPreviewImage(cached));
            return;
        }

        // Chưa có → request server
        client.send(new Message("GET_IMAGE",
                gson.toJson(Map.of("imagePath", serverPath))));
    }

    /**
     * Gọi từ switch "UPLOAD_IMAGE_RESPONSE" trong controller.
     */
    public void onUploadResponse(String payload) {
        try {
            com.google.gson.JsonObject root =
                    gson.fromJson(payload, com.google.gson.JsonObject.class);
            boolean success = root.has("success") && root.get("success").getAsBoolean();

            if (success) {
                String path = root.get("imagePath").getAsString();
                if (onUploadSuccess != null)
                    Platform.runLater(() -> onUploadSuccess.accept(path));
            } else {
                String err = root.has("message")
                        ? root.get("message").getAsString() : "Upload thất bại";
                fireError(err);
            }
        } catch (Exception e) {
            fireError("Lỗi parse upload response: " + e.getMessage());
        }
    }

    /**
     * Gọi từ switch "GET_IMAGE_RESPONSE" trong controller.
     * Chỉ áp dụng nếu imagePath trong response khớp với currentRequestPath.
     */
    public void onGetImageResponse(String payload) {
        try {
            com.google.gson.JsonObject obj =
                    gson.fromJson(payload, com.google.gson.JsonObject.class);
            if (!obj.get("success").getAsBoolean()) return;

            String imgPath = obj.get("imagePath").getAsString();

            // Bỏ qua nếu response không dành cho handler này
            if (!imgPath.equals(currentRequestPath)) return;

            String base64 = obj.get("data").getAsString();
            byte[] bytes  = java.util.Base64.getDecoder().decode(base64);
            Image  img    = new Image(new java.io.ByteArrayInputStream(bytes));

            IMAGE_CACHE.put(imgPath, img);
            Platform.runLater(() -> setPreviewImage(img));

        } catch (Exception e) {
            System.err.println("[ImageUploadHandler] Lỗi GET_IMAGE_RESPONSE: " + e.getMessage());
        }
    }

    /** Xoá preview (dùng khi clearForm). */
    public void clearPreview() {
        currentRequestPath = "";
        if (previewView != null) Platform.runLater(() -> previewView.setImage(null));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE helpers
    // ─────────────────────────────────────────────────────────────────────

    private void setPreviewImage(Image img) {
        if (previewView != null) previewView.setImage(img);
    }

    private void setPreviewFromFile(File file) {
        if (previewView == null) return;
        Platform.runLater(() -> {
            try {
                String lower = file.getName().toLowerCase();
                if (lower.endsWith(".webp") || lower.endsWith(".jfif")) {
                    java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(file);
                    if (bi == null) return;
                    javafx.scene.image.WritableImage wi =
                            new javafx.scene.image.WritableImage(bi.getWidth(), bi.getHeight());
                    javafx.embed.swing.SwingFXUtils.toFXImage(bi, wi);
                    previewView.setImage(wi);
                } else {
                    previewView.setImage(new Image(file.toURI().toString(), true));
                }
            } catch (Exception e) {
                System.err.println("[ImageUploadHandler] Lỗi preview local: " + e.getMessage());
            }
        });
    }

    private void fireError(String msg) {
        if (onError != null) Platform.runLater(() -> onError.accept(msg));
        else System.err.println("[ImageUploadHandler] " + msg);
    }
}