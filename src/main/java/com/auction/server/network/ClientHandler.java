package com.auction.server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import com.auction.client.network.Message;
import com.auction.server.controller.AdminController;
import com.auction.server.controller.AuctionController;
import com.auction.server.controller.UserController;
import com.auction.server.dao.bid.BidDAO;
import com.auction.server.service.AuctionService;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

/**
 * ClientHandler — route message đến đúng controller.
 *
 * FIX 1: thêm route GET_BID_HISTORY → trả lịch sử bid cho LiveBiddingController
 * FIX 2: thêm route GET_BID_HISTORY_BIDDER → trả lịch sử bid của 1 bidder
 */
public class ClientHandler implements Runnable {

    private final Socket             socket;
    private final NetworkServer      server;
    private final Gson               gson = new Gson();

    private final UserController     userController;
    private final AuctionController  auctionController;
    private final AdminController    adminController;
    private final AuctionService     auctionService = new AuctionService();
    private final BidDAO             bidDAO         = new BidDAO();

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

            // — Product detail —
            case "GET_PRODUCT_DETAIL" ->
                send(auctionController.handleGetProductDetail(p));

            // — Auction list —
            case "GET_AUCTIONS" -> send(auctionController.handleGetAuctions());

            // — Seller / Bidder —
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

            // ✅ FIX 1: Lấy lịch sử bid của 1 item — dùng bởi LiveBiddingController khi vào phòng
            // Client gửi: GET_BID_HISTORY { "itemId": "I01" }
            // Server trả: BID_HISTORY_RESPONSE { "history": [ { bidTime, username, bidPrice }, ... ] }
            case "GET_BID_HISTORY" -> {
                try {
                    GetBidHistoryDto dto = gson.fromJson(p, GetBidHistoryDto.class);
                    List<Map<String, Object>> history = bidDAO.getBidHistory(dto.itemId());
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", true,
                            "history", history
                    ))));
                } catch (Exception e) {
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "history", List.of()
                    ))));
                }
            }

            // ✅ FIX 2: Lấy lịch sử bid của 1 bidder — dùng bởi BidHistoryController
            // Client gửi: GET_BID_HISTORY_BIDDER {}
            // Server trả: BID_HISTORY_RESPONSE { "success", "records": [...] }
            case "GET_BID_HISTORY_BIDDER" -> {
                if (!isAuthenticated()) return;
                try {
                    List<Map<String, Object>> records =
                            auctionService.getBidHistoryForBidder(currentUser.getId());
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", true,
                            "records", records
                    ))));
                } catch (Exception e) {
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "message", "Lỗi tải lịch sử: " + e.getMessage()
                    ))));
                }
            }

            // — Admin —
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
                    gson.toJson(Map.of(
                            "message", "Unknown type: " + msg.getType()))));
        }
    }

    public synchronized void send(Message msg) {
        if (out != null) out.println(gson.toJson(msg));
    }

    private boolean isAuthenticated() {
        if (currentUser == null) {
            send(new Message("ERROR", gson.toJson(
                    Map.of("message", "Vui lòng đăng nhập trước."))));
            return false;
        }
        return true;
    }

    private boolean isAdmin() {
        if (!isAuthenticated()) return false;
        if (!"ADMIN".equals(currentUser.getRole())) {
            send(new Message("ERROR", gson.toJson(
                    Map.of("message", "Không có quyền truy cập."))));
            return false;
        }
        return true;
    }

    private String role() {
        return currentUser != null ? currentUser.getRole() : "";
    }

    public boolean isWatchingAuction(String auctionId) {
        return auctionId != null && auctionId.equals(watchingAuctionId);
    }

    private record WatchDto(String auctionId) {}
    private record GetBidHistoryDto(String itemId) {}
}