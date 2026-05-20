package com.auction.server.network;

import com.auction.client.network.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkServer {

    private static final int PORT = 9090;

    // Thread-safe set chứa tất cả client đang kết nối
    private final Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());

    // Thread pool — mỗi client 1 thread
    private final ExecutorService pool =
            Executors.newCachedThreadPool();

    public void start() {
        System.out.println("[Server] Đang khởi động trên port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[Server] Client mới: "
                        + socket.getInetAddress());

                ClientHandler handler =
                        new ClientHandler(socket, this);
                clients.add(handler);
                pool.submit(handler);
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi: " + e.getMessage());
        }
    }

    // Broadcast đến tất cả client xem 1 phiên cụ thể (Observer)
    public void broadcastToAuction(String auctionId, Message msg) {
        synchronized (clients) {
            clients.stream()
                    .filter(c -> c.isWatchingAuction(auctionId))
                    .forEach(c -> c.send(msg));
        }
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[Server] Client ngắt kết nối. " +
                "Còn lại: " + clients.size());
    }
}