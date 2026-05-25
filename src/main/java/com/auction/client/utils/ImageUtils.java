package com.auction.client.utils;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class ImageUtils {

    private static final int   MAX_SIZE = 500;
    private static final float QUALITY  = 0.75f;

    public static String compressToBase64(File file) throws Exception {
        BufferedImage original = readImage(file);
        if (original == null)
            throw new IOException("Không đọc được ảnh: " + file.getName());

        BufferedImage resized = resize(original);
        byte[]        bytes   = toJpegBytes(resized);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // ─────────────────────────────────────────────────────────────────────
    // ĐỌC ẢNH — hỗ trợ jpg/png/bmp/gif (ImageIO) + webp/jfif (JavaFX)
    // ─────────────────────────────────────────────────────────────────────
    private static BufferedImage readImage(File file) throws Exception {

        // 1. Thử ImageIO trước — nhanh, thread-safe, hỗ trợ jpg/png/bmp/gif
        try {
            BufferedImage img = ImageIO.read(file);
            if (img != null) return ensureRGB(img);
        } catch (Exception ignored) {}

        // 2. Fallback: JavaFX Image → SwingFXUtils.fromFXImage()
        //    JavaFX Image phải được tạo trên FX thread hoặc trước khi toolkit khởi động.
        //    Dùng CountDownLatch để chờ FX thread xử lý xong.
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        AtomicReference<String>        error  = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Runnable task = () -> {
            try {
                Image fxImg = new Image(file.toURI().toString(), false);
                if (fxImg.isError()) {
                    error.set("JavaFX không đọc được: " +
                            fxImg.getException().getMessage());
                } else {
                    // SwingFXUtils cần javafx-swing dependency
                    result.set(SwingFXUtils.fromFXImage(fxImg, null));
                }
            } catch (Exception e) {
                error.set(e.getMessage());
            } finally {
                latch.countDown();
            }
        };

        if (Platform.isFxApplicationThread()) {
            // Đang trên FX thread — chạy thẳng (hiếm gặp)
            task.run();
        } else {
            Platform.runLater(task);
            latch.await(); // chờ FX thread xử lý xong
        }

        if (error.get() != null)
            throw new IOException("Không đọc được ảnh: " + file.getName()
                    + " (" + error.get() + ")");

        BufferedImage img = result.get();
        if (img == null)
            throw new IOException("Không đọc được ảnh: " + file.getName());

        return ensureRGB(img);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Đảm bảo ảnh là TYPE_INT_RGB (không có alpha) để JPEG encode không lỗi
    // ─────────────────────────────────────────────────────────────────────
    private static BufferedImage ensureRGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE); // nền trắng cho ảnh có alpha
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    // ─────────────────────────────────────────────────────────────────────
    // RESIZE — scale xuống nếu ảnh lớn hơn MAX_SIZE
    // ─────────────────────────────────────────────────────────────────────
    private static BufferedImage resize(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= MAX_SIZE && h <= MAX_SIZE) return src;

        double scale = (double) MAX_SIZE / Math.max(w, h);
        int newW = (int)(w * scale);
        int newH = (int)(h * scale);

        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                           RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,
                           RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return out;
    }

    // ─────────────────────────────────────────────────────────────────────
    // ENCODE JPEG với quality tùy chỉnh
    // ─────────────────────────────────────────────────────────────────────
    private static byte[] toJpegBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(QUALITY);

        try (var ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null,
                    new javax.imageio.IIOImage(img, null, null), param);
        }
        writer.dispose();
        return baos.toByteArray();
    }
}