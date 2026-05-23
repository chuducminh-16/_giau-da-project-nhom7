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
 * Merge từ 2 nhánh:
 *  - Nhánh gốc : GET_BID_HISTORY → auctionController.handleGetBidHistory() → "BID_HISTORY_RESPONSE"
 *                GET_USER_BID_HISTORY → auctionController.handleGetUserBidHistory() → "USER_BID_HISTORY_RESPONSE"
 *  - Nhánh bạn : GET_BID_HISTORY_ITEM → bidDAO trực tiếp → "BID_HISTORY_RESPONSE"
 *                GET_BID_HISTORY_BIDDER → auctionService trực tiếp → "BID_HISTORY_RESPONSE"
 *
 * Tất cả 4 route được giữ lại vì mỗi cái phục vụ 1 use-case riêng.
 */
public class ClientHandler implements Runnable {

    private final Socket             socket;
    private final NetworkServer      server;
    private final Gson               gson = new Gson();

    private final UserController     userController;
    private final AuctionController  auctionController;
    private final AdminController    adminController;
    // [MERGE] dùng cho GET_BID_HISTORY_ITEM và GET_BID_HISTORY_BIDDER
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

            // — Bid History —

            // [MERGE - Route 1] LiveBiddingController: delegate qua AuctionController
            // Client gửi: GET_BID_HISTORY { "itemId"|"auctionId"|"productId": "..." }
            // Server trả: BID_HISTORY_RESPONSE { "success", "history": [...] }
            case "GET_BID_HISTORY" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleGetBidHistory(p));
            }

            // [MERGE - Route 2] LiveBiddingController (biến thể từ nhánh bạn): gọi bidDAO trực tiếp
            // Client gửi: GET_BID_HISTORY_ITEM { "itemId": "..." }
            // Server trả: BID_HISTORY_RESPONSE { "success", "history": [...] }
            case "GET_BID_HISTORY_ITEM" -> {
                if (!isAuthenticated()) return;
                try {
                    GetBidHistoryItemDto dto = gson.fromJson(p, GetBidHistoryItemDto.class);
                    List<Map<String, Object>> history = bidDAO.getBidHistory(dto.itemId());
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", true,
                            "history", history))));
                } catch (Exception e) {
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "history", List.of()))));
                }
            }

            // [MERGE - Route 3] BidHistoryController: delegate qua AuctionController
            // Client gửi: GET_USER_BID_HISTORY { "bidderId": "..." }
            // Server trả: USER_BID_HISTORY_RESPONSE [...]
            case "GET_USER_BID_HISTORY" -> {
                if (!isAuthenticated()) return;
                send(auctionController.handleGetUserBidHistory(p));
            }

            // [MERGE - Route 4] BidHistoryController (biến thể từ nhánh bạn): gọi auctionService trực tiếp
            // Client gửi: GET_BID_HISTORY_BIDDER {} (dùng currentUser.getId() tự động)
            // Server trả: BID_HISTORY_RESPONSE { "success", "records": [...] }
            case "GET_BID_HISTORY_BIDDER" -> {
                if (!isAuthenticated()) return;
                try {
                    List<Map<String, Object>> records =
                            auctionService.getBidHistoryForBidder(currentUser.getId());
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", true,
                            "records", records))));
                } catch (Exception e) {
                    send(new Message("BID_HISTORY_RESPONSE", gson.toJson(Map.of(
                            "success", false,
                            "message", "Lỗi tải lịch sử: " + e.getMessage()))));
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
    private record GetBidHistoryItemDto(String itemId) {} // [MERGE - Route 2]
}