package com.auction.client.network;

import com.auction.shared.network.Message; // FIX: import đúng package shared
import com.google.gson.Gson;
import javafx.application.Platform;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets; // FIX: thêm import cho UTF-8
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton TCP socket — kết nối đến server một lần duy nhất,
 * dùng chung cho tất cả Controller trong JavaFX.
 */
public class NetworkClient {

    // ── Singleton thread-safe (Initialization-on-demand Holder) ──
    // dùng Holder pattern thay vì if-null check
    // JVM đảm bảo class chỉ được load một lần → thread-safe mà không cần synchronized
    private static class Holder {
        private static final NetworkClient INSTANCE = new NetworkClient();
    }

    public static NetworkClient getInstance() {
        return Holder.INSTANCE;
    }

    private NetworkClient() {} // private: không cho new từ bên ngoài

    // ── Fields ────────────────────────────────────────────
    private Socket         socket;
    private PrintWriter    out;  // ghi message ra server
    private BufferedReader in;   // đọc message từ server
    private final Gson     gson = new Gson();

    // CopyOnWriteArrayList: an toàn khi thêm/xóa listener trong lúc đang iterate
    private final CopyOnWriteArrayList<MessageListener> listeners =
            new CopyOnWriteArrayList<>();

    // ── Kết nối đến server ────────────────────────────────
    /**
     * Gọi một lần duy nhất khi app khởi động (trong MainApp hoặc LoginController).
     * Nếu đã kết nối rồi thì không làm gì thêm.
     */
    public void connect(String host, int port) throws IOException {
        if (socket != null && !socket.isClosed()) {
            System.out.println("[NetworkClient] Đã kết nối rồi, bỏ qua.");
            return;
        }

        socket = new Socket(host, port);

        // thêm StandardCharsets.UTF_8 cho cả out và in
        // Bắt buộc để tiếng Việt không bị vỡ trên Windows
        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.UTF_8),
                true); // autoFlush = true

        in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.UTF_8));

        System.out.println("[NetworkClient] Đã kết nối đến "
                + host + ":" + port);

        // Thread nền liên tục đọc message từ server
        // setDaemon(true): tự tắt khi cửa sổ JavaFX đóng, không giữ app sống
        Thread listenerThread = new Thread(this::listenLoop, "network-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    // ── Gửi message lên server ────────────────────────────
    /**
     * Thread-safe: synchronized để tránh 2 Controller gửi cùng lúc
     * làm vỡ dòng JSON trên socket.
     */
    public synchronized void send(Message msg) {
        if (out == null) {
            System.err.println("[NetworkClient] Chưa kết nối, không thể gửi: " + msg);
            return;
        }
        out.println(gson.toJson(msg)); // ghi đúng 1 dòng JSON + '\n'
    }

    // ── Đăng ký / huỷ listener ───────────────────────────
    /**
     * Controller gọi addListener(this) trong initialize().
     * addIfAbsent: tránh đăng ký trùng cùng một listener.
     */
    public void addListener(MessageListener listener) {
        listeners.addIfAbsent(listener);
    }

    /**
     * Controller gọi removeListener(this) khi màn hình bị đóng.
     * Quan trọng: nếu không remove, listener cũ vẫn nhận message → memory leak
     * và có thể cập nhật UI đã không còn tồn tại → crash.
     */
    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    // ── Vòng lặp đọc từ server (chạy trên network thread) ─
    private void listenLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                final Message msg = gson.fromJson(line, Message.class);

                System.out.println("[NetworkClient] Nhận: " + msg);

                // bọc trong Platform.runLater() để chạy trên JavaFX thread
                // Bắt buộc vì các listener sẽ cập nhật UI (Label, ListView, v.v.)
                // Gọi trực tiếp từ network thread → IllegalStateException → crash
                Platform.runLater(() -> {
                    for (MessageListener listener : listeners) {
                        listener.onMessage(msg);
                    }
                });
            }

        } catch (IOException e) {
            System.err.println("[NetworkClient] Mất kết nối: " + e.getMessage());

        } finally {
            // khi mất kết nối, notify tất cả listener bằng message đặc biệt
            // Controller sẽ hiển thị dialog "Mất kết nối" cho người dùng
            Message lostMsg = new Message("CONNECTION_LOST",
                    "{\"message\":\"Mất kết nối đến server.\"}");

            Platform.runLater(() -> {
                for (MessageListener listener : listeners) {
                    listener.onMessage(lostMsg);
                }
            });
        }
    }

    // ── Ngắt kết nối chủ động ────────────────────────────
    /**
     * Gọi khi app đóng hoặc user logout.
     */
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                System.out.println("[NetworkClient] Đã ngắt kết nối.");
            }
        } catch (IOException e) {
            System.err.println("[NetworkClient] Lỗi khi ngắt kết nối: "
                    + e.getMessage());
        }
    }

    // ── Interface callback ────────────────────────────────
    /**
     * Mỗi Controller implement interface này hoặc dùng lambda.
     *
     * Ví dụ implement trong Controller:
     *
     *   public class BiddingController implements NetworkClient.MessageListener {
     *       @Override
     *       public void onMessage(Message msg) {
     *           switch (msg.getType()) {
     *               case "BID_UPDATE"       -> updatePrice(msg);
     *               case "CONNECTION_LOST"  -> showReconnectDialog();
     *           }
     *       }
     *   }
     *
     * Hoặc dùng lambda (nếu chỉ cần xử lý 1-2 loại message):
     *
     *   NetworkClient.getInstance().addListener(msg -> {
     *       if ("BID_UPDATE".equals(msg.getType())) updatePrice(msg);
     *   });
     */
    @FunctionalInterface
    public interface MessageListener {
        void onMessage(Message message);
    }
}