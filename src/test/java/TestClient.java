

// Cách đơn giản nhất: đặt TestClient trong src/test/java/ (không có subfolder)
// và bỏ khai báo package

import com.auction.shared.network.Message;
import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * File test tạm — KHÔNG có package để tránh lỗi module
 * Đặt tại: src/test/java/TestClient.java
 */
public class TestClient {

    private static final Gson gson = new Gson();
    private static PrintWriter out;

    public static void main(String[] args) throws Exception {

        Socket socket = new Socket("localhost", 9090);

        out = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(),
                        StandardCharsets.UTF_8), true);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(),
                        StandardCharsets.UTF_8));

        // Thread nền đọc phản hồi từ server liên tục
        Thread reader = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    // Parse để in đẹp hơn
                    Message received = gson.fromJson(line, Message.class);
                    System.out.println("  ← [NHẬN] type=" + received.getType()
                            + " payload=" + received.getPayload());
                }
            } catch (IOException e) {
                System.out.println("  ← [MẤT KẾT NỐI]");
            }
        });
        reader.setDaemon(true);
        reader.start();

        System.out.println("Kết nối thành công đến localhost:9090\n");

        // ── Test 1: REGISTER ─────────────────────────────
        System.out.println("→ [GỬI] Test 1: REGISTER");
        send("REGISTER", Map.of(
                "username", "testuser",
                "email",    "test@test.com",
                "password", "123456",
                "fullName", "Test User",
                "phone",    "0123456789",
                "address",  "Hanoi",
                "role",     "BIDDER"
        ));
        Thread.sleep(600);

        // ── Test 2: LOGIN đúng ───────────────────────────
        System.out.println("→ [GỬI] Test 2: LOGIN đúng");
        send("LOGIN", Map.of(
                "email",    "test@test.com",
                "password", "123456"
        ));
        Thread.sleep(600);

        // ── Test 3: LOGIN sai mật khẩu ──────────────────
        System.out.println("→ [GỬI] Test 3: LOGIN sai mật khẩu");
        send("LOGIN", Map.of(
                "email",    "test@test.com",
                "password", "sai_mat_khau"
        ));
        Thread.sleep(600);

        // ── Test 4: GET_AUCTIONS ─────────────────────────
        System.out.println("→ [GỬI] Test 4: GET_AUCTIONS");
        send("GET_AUCTIONS", Map.of());
        Thread.sleep(600);

        // ── Test 5: WATCH_AUCTION ────────────────────────
        System.out.println("→ [GỬI] Test 5: WATCH_AUCTION");
        send("WATCH_AUCTION", Map.of("auctionId", "auction-001"));
        Thread.sleep(600);

        // ── Test 6: PLACE_BID ────────────────────────────
        System.out.println("→ [GỬI] Test 6: PLACE_BID");
        send("PLACE_BID", Map.of(
                "productId", "auction-001",
                "amount",    1500000.0
        ));
        Thread.sleep(600);

        // ── Test 7: Lệnh không hợp lệ ───────────────────
        System.out.println("→ [GỬI] Test 7: LỆNH SAI");
        send("LENH_KHONG_TON_TAI", Map.of());
        Thread.sleep(600);

        // ── Test 8: UNWATCH_AUCTION ──────────────────────
        System.out.println("→ [GỬI] Test 8: UNWATCH_AUCTION");
        send("UNWATCH_AUCTION", Map.of());
        Thread.sleep(600);

        socket.close();
        System.out.println("\n=== Hoàn tất tất cả test ===");
    }

    // Helper gọn: tạo Message và gửi 1 dòng JSON
    private static void send(String type, Object payload) {
        String payloadJson = gson.toJson(payload);
        Message msg = new Message(type, payloadJson);
        out.println(gson.toJson(msg));
    }
}