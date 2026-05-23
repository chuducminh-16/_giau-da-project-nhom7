package com.auction.server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.auction.client.network.Message;
import com.auction.server.controller.AdminController;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.UserController;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

/**
 * Chỉ lo 2 việc:
 *   1. Đọc/ghi socket (network)
 *   2. Route message đến đúng controller
 * Mọi business logic đều nằm trong Controller và Service.
 */
public class ClientHandler implements Runnable {

    private final Socket             socket;
    private final NetworkServer      server;
    private final Gson               gson = new Gson();

    private final UserController     userController;
    private final AuctionController  auctionController;
    private final AdminController    adminController;

    private PrintWriter    out;
    private BufferedReader in;
    private User           currentUser;
    private String         watchingAuctionId;

    public ClientHandler(Socket socket, NetworkServer server) {
        this.socket            = socket;
        this.server            = server;
        this.userController    = new UserController();
        this.auctionController = new AuctionController(server);
        this.adminController   = new AdminController(server);
    }

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);
                System.out.println("[Server] Nhận: " + msg.getType());
                route(msg);
            }
        } catch (IOException e) {
            System.out.println("[ClientHandler] Client ngắt kết nối.");
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void route(Message msg) {
        String p = msg.getPayload();

        switch (msg.getType()) {

            // — Auth —
            case "LOGIN" -> {
                UserController.LoginResult r = userController.handleLogin(p);
                if (r.user() != null) currentUser = r.user();
                send(r.response());
            }
            case "REGISTER" -> send(userController.handleRegister(p));

            // — Product detail (dùng bởi DetailController & LiveBiddingController) —
            // Không yêu cầu authentication — ai cũng có thể xem
            case "GET_PRODUCT_DETAIL" ->
                send(auctionController.handleGetProductDetail(p));

            // — Auction list —
            case "GET_AUCTIONS" -> send(auctionController.handleGetAuctions());

            // — Seller / Bidder (yêu cầu đăng nhập) —
            case "GET_MY_PRODUCTS" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleGetMyProducts(p));
            }
            case "ADD_PRODUCT" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleAddProduct(p, role()));
            }
            case "UPDATE_PRODUCT" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleUpdateProduct(p, role()));
            }
            case "DELETE_PRODUCT" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleDeleteProduct(p, role()));
            }
            case "WATCH_AUCTION" -> {
                WatchDto dto = gson.fromJson(p, WatchDto.class);
                this.watchingAuctionId = dto.auctionId();
            }
            case "PLACE_BID" -> {
                if (!isAuthenticated()) return;
                auctionController.handlePlaceBid(
                        p, role(), currentUser.getId(),
                        currentUser.getUsername(), this);
            }
            // ── FIX: GET_BID_HISTORY (ban goc THIEU case nay) ─────────────
            case "GET_BID_HISTORY" -> {
                // Khong can dang nhap — ai cung xem duoc lich su
                send(auctionController.handleGetBidHistory(p));
            }

            // ── THÊM MỚI: Định tuyến lấy lịch sử tổng hợp của cá nhân người dùng ─────────────
            case "GET_USER_BID_HISTORY" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleGetUserBidHistory(p));
            }

            // ── FIX: Auto-Bid routes (ban goc THIEU 2 case nay) ──────────
            case "REGISTER_AUTO_BID" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleRegisterAutoBid(p, currentUser.getId()));
            }
            case "CANCEL_AUTO_BID" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleCancelAutoBid(p, currentUser.getId()));
            }

            // ── Admin ─────────────────────────────────────────────────────
            case "ADMIN_GET_PRODUCTS" -> {
                if (!isAdmin()) return;
                send(adminController.handleGetProducts());
            }
            case "ADMIN_GET_USERS" -> {
                if (!isAdmin()) return;
                send(adminController.handleGetUsers());
            }
            case "ADMIN_GET_AUCTIONS" -> {
                if (!isAdmin()) return;
                send(adminController.handleGetAuctions());
            }
            case "ADMIN_DELETE_PRODUCT" -> {
                if (!isAdmin()) return;
                send(adminController.handleDeleteProduct(p));
            }
            case "ADMIN_DELETE_USER" -> {
                if (!isAdmin()) return;
                send(adminController.handleDeleteUser(p, currentUser.getId()));
            }
            case "ADMIN_FORCE_CLOSE_AUCTION" -> {
                if (!isAdmin()) return;
                send(adminController.handleForceCloseAuction(p));
            }

            default -> send(new Message("ERROR",
                    gson.toJson(java.util.Map.of(
                            "message", "Unknown type: " + msg.getType()))));
        }
    }

    public synchronized void send(Message msg) {
        if (out != null) out.println(gson.toJson(msg));
    }

    private boolean isAuthenticated() {
        if (currentUser == null) {
            send(new Message("ERROR", gson.toJson(
                    java.util.Map.of("message", "Vui lòng đăng nhập trước."))));
            return false;
        }
        return true;
    }

    private boolean isAdmin() {
        if (!isAuthenticated()) return false;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(new Message("ERROR", gson.toJson(
                    java.util.Map.of("message", "Không có quyền truy cập."))));
            return false;
        }
        return true;
    }

    private String role() {
        return currentUser != null ? currentUser.getRole() : "";
    }

    public boolean isWatchingAuction(String auctionId) {
        return auctionId.equals(watchingAuctionId);
    }

    private record WatchDto(String auctionId) {}
}