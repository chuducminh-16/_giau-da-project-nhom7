package com.auction.server.network;

import com.auction.client.network.Message;
import com.auction.server.controller.AdminController;
import com.auction.server.controller.AuctionRoomEngineController;
import com.auction.server.controller.AutoBidController;
import com.auction.server.controller.ProductController;
import com.auction.server.controller.UserController;
import com.auction.server.controller.WalletController;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

import java.util.Map;

/**
 * MessageRouter - Phan luong xu ly goi tin tu Client den Controller phu hop.
 *
 * Tach ra tu ClientHandler de giam do phuc tap cua vong lap Socket chinh.
 * ClientHandler chi can goi router.route(msg) sau khi doc duoc message.
 *
 * Cac nhom message:
 *   - Auth:    LOGIN, REGISTER, UPDATE_PROFILE
 *   - Auction: GET_AUCTIONS, WATCH_AUCTION, PLACE_BID, GET_BID_HISTORY, ...
 *   - Product: GET_MY_PRODUCTS, ADD_PRODUCT, UPDATE_PRODUCT, DELETE_PRODUCT, GET_PRODUCT_DETAIL
 *   - Wallet:  TOP_UP, GET_BALANCE
 *   - AutoBid: REGISTER_AUTO_BID, CANCEL_AUTO_BID
 *   - Image:   UPLOAD_IMAGE, GET_IMAGE
 *   - Admin:   ADMIN_GET_PRODUCTS, ADMIN_GET_USERS, ADMIN_GET_AUCTIONS, ADMIN_DELETE_*, ADMIN_FORCE_CLOSE_AUCTION
 */
public class MessageRouter {

    private final ClientHandler handler;
    private final Gson gson;

    // Controllers
    private final UserController              userController;
    private final AuctionRoomEngineController auctionController;
    private final ProductController           productController;
    private final AutoBidController           autoBidController;
    private final AdminController             adminController;
    private final ImageHandler                imageHandler;

    // Session state (giu tham chieu tu ClientHandler)
    private User   currentUser;
    private String watchingAuctionId;

    public MessageRouter(
            ClientHandler handler,
            Gson gson,
            UserController userController,
            AuctionRoomEngineController auctionController,
            ProductController productController,
            AutoBidController autoBidController,
            AdminController adminController,
            ImageHandler imageHandler
    ) {
        this.handler           = handler;
        this.gson              = gson;
        this.userController    = userController;
        this.auctionController = auctionController;
        this.productController = productController;
        this.autoBidController = autoBidController;
        this.adminController   = adminController;
        this.imageHandler      = imageHandler;
    }

    // =========================================================================
    // Getters / Setters de ClientHandler dong bo session state
    // =========================================================================

    public User   getCurrentUser()       { return currentUser; }
    public void   setCurrentUser(User u) { this.currentUser = u; }
    public String getWatchingAuctionId() { return watchingAuctionId; }

    public boolean isWatchingAuction(String auctionId) {
        return auctionId != null && auctionId.equals(watchingAuctionId);
    }

    // =========================================================================
    // Phan luong chinh
    // =========================================================================

    /**
     * Nhan message da parse san, tim controller phu hop va goi xuong.
     *
     * @param msg Message da duoc parse tu JSON
     */
    public void route(Message msg) {
        String p = msg.getPayload();

        switch (msg.getType()) {

            // --- Xac thuc & Tai khoan ---
            case "LOGIN"  -> {
                var loginResult = userController.handleLogin(p);
                if (loginResult != null) {
                    if (loginResult.user() != null) currentUser = loginResult.user();
                    handler.send(loginResult.response());
                }
            }
            case "REGISTER"       -> handler.send(userController.handleRegister(p));
            case "UPDATE_PROFILE" -> {
                if (!isAuthenticated()) return;
                Message responseMsg = userController.handleUpdateProfile(p);
                // Dong bo RAM Server
                try {
                    @SuppressWarnings("unchecked") Map<String, Object> data = gson.fromJson(p, Map.class);
                    if (data.containsKey("fullName")) currentUser.setFullName((String) data.get("fullName"));
                    if (data.containsKey("email"))    currentUser.setEmail((String) data.get("email"));
                    if (data.containsKey("phone"))    currentUser.setPhone((String) data.get("phone"));
                    if (data.containsKey("address"))  currentUser.setAddress((String) data.get("address"));
                } catch (Exception ignored) {}
                handler.send(responseMsg);
            }

            // --- Phong Dau gia Realtime ---
            case "GET_AUCTIONS"      -> handler.send(auctionController.handleGetAuctions());
            case "WATCH_AUCTION"     -> {
                record WatchDto(String auctionId) {}
                WatchDto dto = gson.fromJson(p, WatchDto.class);
                this.watchingAuctionId = dto.auctionId();
            }
            case "PLACE_BID"         -> {
                if (!isAuthenticated()) return;
                auctionController.handlePlaceBid(p, role(), currentUser.getId(), currentUser.getUsername(), handler);
            }
            case "GET_BID_HISTORY"      -> handler.send(auctionController.handleGetBidHistory(p));
            case "GET_USER_BID_HISTORY" -> {
                if (!isAuthenticated()) return;
                handler.send(auctionController.handleGetUserBidHistory(p));
            }
            case "GET_PRODUCT_HISTORY"  -> handler.send(auctionController.handleGetProductHistory(p));

            // --- Quan ly San pham ---
            case "GET_MY_PRODUCTS"   -> {
                if (!isAuthenticated()) return;
                handler.send(productController.handleGetMyProducts(p));
            }
            case "ADD_PRODUCT"       -> {
                if (!isAuthenticated()) return;
                handler.send(productController.handleAddProduct(p, role()));
            }
            case "UPDATE_PRODUCT"    -> {
                if (!isAuthenticated()) return;
                handler.send(productController.handleUpdateProduct(p, role()));
            }
            case "DELETE_PRODUCT"    -> {
                if (!isAuthenticated()) return;
                handler.send(productController.handleDeleteProduct(p, role()));
            }
            case "GET_PRODUCT_DETAIL" -> handler.send(productController.handleGetProductDetail(p));

            // --- Vi tien ---
            case "TOP_UP"     -> {
                if (!isAuthenticated()) return;
                handler.send(new WalletController().handleTopUp(p));
            }
            case "GET_BALANCE" -> {
                if (!isAuthenticated()) return;
                handler.send(new WalletController().handleGetBalance(p));
            }

            // --- Auto-Bid ---
            case "REGISTER_AUTO_BID" -> {
                if (!isAuthenticated()) return;
                handler.send(autoBidController.handleRegisterAutoBid(p, currentUser.getId()));
            }
            case "CANCEL_AUTO_BID"   -> {
                if (!isAuthenticated()) return;
                handler.send(autoBidController.handleCancelAutoBid(p, currentUser.getId()));
            }

            // --- Hinh anh ---
            case "UPLOAD_IMAGE" -> {
                if (!isAuthenticated()) return;
                handler.send(imageHandler.handleUploadImage(p));
            }
            case "GET_IMAGE" -> handler.send(imageHandler.handleGetImage(p));

            // --- Admin ---
            case "ADMIN_GET_PRODUCTS"         -> { if (!isAdmin()) return; handler.send(adminController.handleGetProducts()); }
            case "ADMIN_GET_USERS"            -> { if (!isAdmin()) return; handler.send(adminController.handleGetUsers()); }
            case "ADMIN_GET_AUCTIONS"         -> { if (!isAdmin()) return; handler.send(adminController.handleGetAuctions()); }
            case "ADMIN_DELETE_PRODUCT"       -> { if (!isAdmin()) return; handler.send(adminController.handleDeleteProduct(p)); }
            case "ADMIN_DELETE_USER"          -> { if (!isAdmin()) return; handler.send(adminController.handleDeleteUser(p, currentUser.getId())); }
            case "ADMIN_FORCE_CLOSE_AUCTION"  -> { if (!isAdmin()) return; handler.send(adminController.handleForceCloseAuction(p)); }

            // --- Khong xac dinh ---
            default -> handler.send(new Message("ERROR",
                    gson.toJson(Map.of("message", "Unknown type: " + msg.getType()))));
        }
    }

    // =========================================================================
    // Kiem tra quyen truy cap
    // =========================================================================

    private boolean isAuthenticated() {
        if (currentUser == null) {
            handler.send(new Message("ERROR", gson.toJson(
                    Map.of("message", "Vui long dang nhap truoc khi thuc hien thao tac nay."))));
            return false;
        }
        return true;
    }

    private boolean isAdmin() {
        if (!isAuthenticated()) return false;
        if (!"ADMIN".equals(currentUser.getRole())) {
            handler.send(new Message("ERROR", gson.toJson(
                    Map.of("message", "Tu choi truy cap: Ban khong co quyen quan tri vien."))));
            return false;
        }
        return true;
    }

    private String role() {
        return currentUser != null ? currentUser.getRole() : "";
    }
}