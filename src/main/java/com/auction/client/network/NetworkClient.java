package com.auction.client.network;

import com.google.gson.Gson;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleton TCP socket — kết nối đến server một lần duy nhất,
 * dùng chung cho tất cả Controller.
 */
public class NetworkClient {

    // ── Singleton ──────────────────────────────────────
    private static NetworkClient instance;

    public static NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    private NetworkClient() {} // private: không cho new bên ngoài

    // ── Fields ──────────────────────────────────────────
    private Socket         socket;
    private PrintWriter    out;       // ghi ra server
    private BufferedReader in;        // đọc từ server
    private final Gson     gson = new Gson();

    // Danh sách listener — mỗi Controller đăng ký 1 cái
    private final CopyOnWriteArrayList<MessageListener> listeners =
            new CopyOnWriteArrayList<>();

    // ── Kết nối ─────────────────────────────────────────
    public void connect(String host, int port) throws IOException {
        if (socket != null && !socket.isClosed()) return; // đã kết nối rồi

        socket = new Socket(host, port);
        out = new PrintWriter(socket.getOutputStream(), true);
        in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Thread nền: liên tục đọc message từ server
        Thread t = new Thread(this::listenLoop, "network-listener");
        t.setDaemon(true); // tự tắt khi app đóng
        t.start();
    }

    // ── Gửi message ─────────────────────────────────────
    public synchronized void send(Message msg) {
        if (out != null) {
            out.println(gson.toJson(msg)); // ghi 1 dòng JSON xuống socket
        }
    }

    // ── Đăng ký / huỷ listener ──────────────────────────
    public void addListener(MessageListener listener) {
        listeners.addIfAbsent(listener);
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    // ── Vòng lặp đọc từ server (chạy trên thread riêng) ─
    private void listenLoop() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                // Parse JSON → Message
                Message msg = gson.fromJson(line, Message.class);
                // Thông báo cho tất cả listener đang đăng ký
                for (MessageListener l : listeners) {
                    l.onMessage(msg);
                }
            }
        } catch (IOException e) {
            System.err.println("[NetworkClient] Mất kết nối: " + e.getMessage());
        }
    }

    // ── Interface callback ───────────────────────────────
    @FunctionalInterface
    public interface MessageListener {
        void onMessage(Message message);
    }
}