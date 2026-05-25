package com.auction.client.utils;

import com.auction.client.network.NetworkClient;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ImageUploadHandler — upload ảnh lên ImgBB, lưu URL vào DB.
 *
 * Interface giữ nguyên 100% so với bản cũ:
 *   - pickAndUpload(window)
 *   - loadFromServer(serverPath)
 *   - onUploadResponse(payload)   ← không dùng nữa nhưng giữ để không lỗi compile
 *   - onGetImageResponse(payload) ← không dùng nữa nhưng giữ để không lỗi compile
 *   - clearPreview()
 *   - onSuccess(callback)
 *   - onError(callback)
 *
 * ManageProductController KHÔNG cần sửa gì.
 * ClientHandler KHÔNG cần sửa gì.
 */
public class ImageUploadHandler {

    private static final String IMGBB_API_KEY = "d0f0926c9891fa0a282354311d5835c8";
    private static final String IMGBB_URL     = "https://api.imgbb.com/1/upload";

    // Cache URL → Image để không load lại nhiều lần
    private static final Map<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private final NetworkClient client;      // giữ để không lỗi compile, không dùng nữa
    private final ImageView     previewView;
    private final Gson          gson = new Gson();

    private Consumer<String> onUploadSuccess;
    private Consumer<String> onError;

    private String currentRequestPath = "";

    public ImageUploadHandler(NetworkClient client, ImageView previewView) {
        this.client      = client;
        this.previewView = previewView;
    }

    public ImageUploadHandler onSuccess(Consumer<String> callback) {
        this.onUploadSuccess = callback;
        return this;
    }

    public ImageUploadHandler onError(Consumer<String> callback) {
        this.onError = callback;
        return this;
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Mở FileChooser, nén ảnh, upload lên ImgBB, trả về URL công khai.
     * URL này được lưu vào DB thay cho local path cũ.
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

        // Upload lên ImgBB trong background thread
        new Thread(() -> {
            try {
                String base64 = ImageUtils.compressToBase64(file);
                String imgbbUrl = uploadToImgBB(base64);

                if (imgbbUrl != null) {
                    // Cache luôn để loadFromServer không phải load lại
                    IMAGE_CACHE.put(imgbbUrl, previewView.getImage());
                    if (onUploadSuccess != null)
                        Platform.runLater(() -> onUploadSuccess.accept(imgbbUrl));
                } else {
                    fireError("Upload ImgBB thất bại, vui lòng thử lại.");
                }
            } catch (Exception e) {
                fireError("Lỗi upload: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Load ảnh từ URL (ImgBB) hoặc path cũ (local — fallback, sẽ null nếu file không tồn tại).
     * Nếu là URL http/https → load thẳng bằng JavaFX Image.
     * Nếu là path local cũ → thử load file, không có thì bỏ qua.
     */
    public void loadFromServer(String serverPath) {
        if (serverPath == null || serverPath.isBlank()) {
            if (previewView != null) Platform.runLater(() -> previewView.setImage(null));
            return;
        }

        currentRequestPath = serverPath;

        // Kiểm tra cache
        Image cached = IMAGE_CACHE.get(serverPath);
        if (cached != null) {
            Platform.runLater(() -> setPreviewImage(cached));
            return;
        }

        // URL ImgBB (http/https) → JavaFX Image load trực tiếp
        if (serverPath.startsWith("http://") || serverPath.startsWith("https://")) {
            new Thread(() -> {
                try {
                    Image img = new Image(serverPath, true); // background load
                    IMAGE_CACHE.put(serverPath, img);
                    Platform.runLater(() -> setPreviewImage(img));
                } catch (Exception e) {
                    System.err.println("[ImageUploadHandler] Lỗi load URL: " + e.getMessage());
                }
            }).start();
            return;
        }

        // Path local cũ (fallback) — thử load file
        File f = new File(serverPath);
        if (f.exists()) {
            setPreviewFromFile(f);
        } else {
            // File không tồn tại trên máy này → clear preview
            if (previewView != null) Platform.runLater(() -> previewView.setImage(null));
        }
    }

    /**
     * Giữ nguyên để ManageProductController không lỗi compile.
     * Không còn dùng vì upload đã chuyển sang ImgBB trực tiếp.
     */
    public void onUploadResponse(String payload) {
        // No-op — upload giờ xử lý trong pickAndUpload() trực tiếp
    }

    /**
     * Giữ nguyên để ManageProductController không lỗi compile.
     * Không còn dùng vì load ảnh dùng URL ImgBB trực tiếp.
     */
    public void onGetImageResponse(String payload) {
        // No-op — load ảnh giờ dùng loadFromServer() với URL trực tiếp
    }

    /** Xoá preview (dùng khi clearForm). */
    public void clearPreview() {
        currentRequestPath = "";
        if (previewView != null) Platform.runLater(() -> previewView.setImage(null));
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE — ImgBB upload
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Upload Base64 lên ImgBB, trả về URL display (url) của ảnh.
     * Dùng multipart/form-data đơn giản.
     *
     * @return URL công khai dạng https://i.ibb.co/xxx/yyy.jpg, hoặc null nếu lỗi
     */
    private String uploadToImgBB(String base64Data) throws Exception {
        String boundary = "----FormBoundary" + System.currentTimeMillis();
        String apiUrl   = IMGBB_URL + "?key=" + IMGBB_API_KEY;

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("Content-Type",
                "multipart/form-data; boundary=" + boundary);

        // Build multipart body
        StringBuilder sb = new StringBuilder();
        sb.append("--").append(boundary).append("\r\n");
        sb.append("Content-Disposition: form-data; name=\"image\"").append("\r\n");
        sb.append("\r\n");
        sb.append(base64Data).append("\r\n");
        sb.append("--").append(boundary).append("--").append("\r\n");

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            System.err.println("[ImgBB] HTTP " + status);
            return null;
        }

        // Đọc response
        String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject json = gson.fromJson(response, JsonObject.class);

        if (!json.get("success").getAsBoolean()) return null;

        // Lấy URL trực tiếp của ảnh (không phải trang web ImgBB)
        return json.getAsJsonObject("data").get("url").getAsString();
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE — UI helpers
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