package com.auction.server.network;

import com.auction.shared.network.Message;
import com.google.gson.Gson;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkServer {

    private static final int PORT = 9090;
    private final Gson gson = new Gson();

    // Thread-safe set chứa tất cả client đang kết nối
    private final Set<ClientHandler> clients =
            Collections.synchronizedSet(new HashSet<>());

    // Thread pool — mỗi client 1 thread
    private final ExecutorService pool =
            Executors.newCachedThreadPool();
    
    // tách serverSocket ra ngoài để stop() sau này
    private ServerSocket serverSocket;

    public void start() {
        System.out.println("[Server] Đang khởi động trên port " + PORT);
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("[Server] Sẵn sàng nhận kết nối...");


            while (!serverSocket.isClosed()) {
                Socket socket = serverSocket.accept();
                System.out.println("[Server] Client mới: "
                        + socket.getInetAddress() + ":" + socket.getPort());

                ClientHandler handler =
                        new ClientHandler(socket, this);
                clients.add(handler);
                pool.submit(handler);  // chạy handler trong thread pool
            }
        } catch (IOException e) {

            if (!serverSocket.isClosed()) {
                System.err.println("[Server] Lỗi nghiêm trọng: " + e.getMessage());
            }
        }
    }

    // Dừng server
    public void stop() {
        System.out.println("[Server] Đang dừng server...");
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[Server] Lỗi khi đóng serverSocket: " + e.getMessage());
        }
        pool.shutdown(); // dừng nhận task mới, chờ task hiện tại xong
        System.out.println("[Server] Server đã dừng.");
    }

    // Broadcast đến tất cả client xem 1 phiên cụ thể (Observer)
    public void broadcastToAuction(String auctionId, Message msg) {
        synchronized (clients) {
            long count = clients.stream()
                    .filter(c -> c.isWatchingAuction(auctionId))
                    .peek(c -> c.send(msg))
                    .count();
            System.out.println("[Server] Broadcast BID_UPDATE đến "
                    + count + " client đang xem phiên " + auctionId);
        }
    }

    // Xóa client khi ngắt kết nối
    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        System.out.println("[Server] Client ngắt kết nối. " +
                "Còn lại: " + clients.size());
    }

    // Broadcast đến TẤT CẢ client (dùng cho AUCTION_ENDED)
    public void broadcast(Message msg) {
        synchronized (clients) {
            System.out.println("[Server] Broadcast toàn cục đến "
                    + clients.size());
            clients.forEach(c -> c.send(msg));
        }
    }

    // Entry point chạy server
    public static void main(String[] args) {
        new NetworkServer().start();
    }

}