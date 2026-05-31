package com.auction.server.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;

import com.auction.client.network.Message;
import com.auction.server.controller.AdminController;
import com.auction.server.controller.AuctionRoomEngineController;
import com.auction.server.controller.AutoBidController;
import com.auction.server.controller.ProductController;
import com.auction.server.controller.UserController;
import com.auction.server.controller.WalletController;
import com.auction.shared.model.Entity.User.User;
import com.google.gson.Gson;

/**
 * ClientHandler - Duy tri ket noi Socket voi tung Client.
 *
 * Sau khi refactor, class nay chi chiu trach nhiem:
 *   1. Quan ly vong lap doc/ghi Socket (I/O)
 *   2. Parse JSON thanh doi tuong Message
 *   3. Uy quyen dinh tuyen (routing) cho MessageRouter
 *   4. Cung cap phuong thuc send() thread-safe cho cac Controller dung
 *
 * Moi logic nghiep vu da duoc chuyen sang MessageRouter.
 */
public class ClientHandler implements Runnable {

    private final Socket        socket;
    private final NetworkServer server;
    private final Gson          gson = new Gson();
    private final MessageRouter router;

    private PrintWriter   out;
    private BufferedReader in;

    public ClientHandler(Socket socket, NetworkServer server) {
        this.socket = socket;
        this.server = server;

        // Khoi tao tat ca Controllers va uy quyen cho Router
        UserController              userController    = new UserController();
        AuctionRoomEngineController auctionController = new AuctionRoomEngineController(server);
        ProductController           productController = new ProductController();
        AutoBidController           autoBidController = new AutoBidController();
        AdminController             adminController   = new AdminController(server);
        ImageHandler                imageHandler      = new ImageHandler(this.gson);

        this.router = new MessageRouter(
                this, gson,
                userController, auctionController,
                productController, autoBidController,
                adminController, imageHandler
        );
    }

    // =========================================================================
    // Vong lap Socket chinh
    // =========================================================================

    @Override
    public void run() {
        try {
            out = new PrintWriter(socket.getOutputStream(), true);
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = gson.fromJson(line, Message.class);
                System.out.println("[Server] Nhan: " + msg.getType());
                router.route(msg);
            }
        } catch (IOException e) {
            System.out.println("[ClientHandler] Client ngat ket noi.");
        } finally {
            server.removeClient(this);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // =========================================================================
    // Gui tin thread-safe
    // =========================================================================

    /**
     * Gui du lieu dong bo (Thread-safe) ve phia client qua Output Stream.
     * Cac Controller goi phuong thuc nay de tra loi Client.
     */
    public synchronized void send(Message msg) {
        if (out != null) out.println(gson.toJson(msg));
    }

    // =========================================================================
    // Trang thai phong dau gia dang xem
    // =========================================================================

    /**
     * Kiem tra xem Client nay co dang mo man hinh phong dau gia nay khong.
     * Dung boi NetworkServer de broadcast BID_UPDATE/AUCTION_ENDED.
     */
    public boolean isWatchingAuction(String auctionId) {
        return router.isWatchingAuction(auctionId);
    }
}