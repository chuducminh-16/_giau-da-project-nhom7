package com.auction.server.network;

import com.auction.client.network.Message;
import com.auction.server.scheduler.AuctionScheduler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NetworkServer — máy chủ TCP chính.
 *
 * PATCH: tích hợp AuctionScheduler để tự động đóng phiên hết giờ.
 */
public class NetworkServer {

    private static final int PORT = 9090;

    // Thread-safe set chứa tất cả client đang kết nối
    private final Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());

    // Thread pool — mỗi client 1 thread
    private final ExecutorService pool =
            Executors.newCachedThreadPool();

    // Scheduler tự động đóng phiên hết giờ
    private final AuctionScheduler scheduler = new AuctionScheduler(this);

    public void start() {
        System.out.println("[Server] Đang khởi động trên port " + PORT);

        // Khởi động scheduler TRƯỚC KHI chấp nhận kết nối
        scheduler.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[Server] Client mới: " + socket.getInetAddress());
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                pool.submit(handler);
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi: " + e.getMessage());
        } finally {
            scheduler.stop();
            pool.shutdown();
        }
    }

    /**
     * Broadcast đến tất cả client đang xem 1 phiên cụ thể (Observer Pattern).
     * Dùng cho: BID_UPDATE, AUCTION_ENDED, TIME_EXTENDED.
     */
    public void broadcastToAuction(String auctionId, Message msg) {
        synchronized (clients) {
            clients.stream()
                    .filter(c -> c.isWatchingAuction(auctionId))
                    .forEach(c -> c.send(msg));
        }
    }

    /**
     * Broadcast đến TẤT CẢ client đang kết nối.
     * Dùng cho thông báo hệ thống (maintenance, v.v.)
     */
    public void broadcastToAll(Message msg) {
        synchronized (clients) {
            clients.forEach(c -> c.send(msg));
        }
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[Server] Client ngắt kết nối. Còn lại: " + clients.size());
    }

    public int getClientCount() {
        return clients.size();
    }
}