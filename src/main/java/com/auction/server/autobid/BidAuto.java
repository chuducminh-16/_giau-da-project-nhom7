package com.auction.server.autobid;

import com.auction.client.network.Message;
import com.auction.server.network.NetworkServer;
import com.auction.server.service.AutoBidService;
import com.google.gson.Gson;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * =========================================================================
 * THƯ MỤC: com.auction.server.autobid
 * CLASS: BidAuto (Áp dụng Design Pattern: Singleton & Producer-Consumer)
 * * CHỨC NĂNG CỐT LÕI:
 * - Hoạt động như một Event Bus (Vòng lặp sự kiện) chạy độc lập, ngầm hoàn toàn.
 * - Lắng nghe, xếp hàng và xử lý tuần tự (Single-Thread) các sự kiện đặt giá tự động.
 * - Loại bỏ hoàn toàn sự phụ thuộc vào AuctionService cũ đã bị phân rã.
 * =========================================================================
 */
public class BidAuto {

    // ── Thiết kế Design Pattern: Singleton (Khởi tạo lười thông qua Holder) ──
    private static final class Holder {
        // Đảm bảo thực thể duy nhất của BidAuto chỉ được tạo ra khi có lệnh gọi đầu tiên
        static final BidAuto INSTANCE = new BidAuto();
    }

    /**
     * Hàm toàn cục lấy ra thực thể duy nhất của luồng tự động đặt giá
     */
    public static BidAuto getInstance() {
        return Holder.INSTANCE;
    }

    // Constructor private để chặn tuyệt đối hành vi dùng lệnh 'new' từ bên ngoài
    private BidAuto() {}

    // ── Các thuộc tính điều khiển và luồng lưu trữ (Fields) ─────────────────

    // Queue Thread-safe không giới hạn phần tử: ClientHandler đẩy vào (Produce), Worker lấy ra (Consume)
    private final BlockingQueue<RecordBid> queue = new LinkedBlockingQueue<>();

    // Sử dụng duy nhất 1 Worker Thread để xử lý TUẦN TỰ mọi lượt Auto-bid trên sàn.
    // ĐIỀU NÀY CỰC KỲ QUAN TRỌNG: Nó ngăn chặn hoàn toàn hiện tượng Race Condition (hai bot tự nâng giá đè nhau) ở tầng logic.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auto-bid-worker");
        t.setDaemon(true); // Đặt trạng thái Daemon để luồng này tự động giải phóng khi Server tắt hẳn
        return t;
    });

    private NetworkServer server;          // Đối tượng Server điều phối kết nối mạng mạng Socket
    private AutoBidService autoBidService;  // Dịch vụ chứa bộ não tính toán bước giá tự động
    private final Gson gson = new Gson();   // Công cụ đúc chuỗi JSON chuyển vận qua mạng

    // Biến cờ hiệu volatile để đảm bảo tính đồng bộ bộ nhớ tức thì giữa các luồng hệ thống
    private volatile boolean running = false;

    // ── Vòng đời: Kích hoạt / Chấm dứt (Start / Stop) ─────────────────────────

    /**
     * Khởi động bộ máy xử lý tự động đặt giá.
     * Thường được gọi trực tiếp bên trong NetworkServer.start() ngay khi bật Server.
     *
     * @param server Thực thể mạng chính để broadcast gói tin "BID_UPDATE" ra toàn sàn
     */
    public void start(NetworkServer server) {
        if (running) return; // Nếu luồng đang chạy ngầm rồi thì bỏ qua không khởi chạy trùng lặp
        
        this.server = server;
        
        // SỬA LỖI TẠI ĐÂY: Khởi tạo AutoBidService bằng Constructor mặc định (Không truyền AuctionService cũ nữa)
        this.autoBidService = new AutoBidService();
        this.running = true;

        // Đẩy luồng lặp vô tận processLoop vào hàng đợi thực thi của đơn luồng Worker
        worker.submit(this::processLoop);
        System.out.println("[✓] [BidAuto] Event Bus đã khởi động thành công. Sẵn sàng gánh vác các bot đặt giá.");
    }

    /**
     * Dừng khẩn cấp toàn bộ hệ thống bot đặt giá khi Server đóng cửa
     */
    public void stop() {
        running = false;
        worker.shutdownNow(); // Ép buộc luồng worker đang ngủ trong hàng đợi phải tỉnh dậy và hủy bỏ
        System.out.println("[!] [BidAuto] Đã phát lệnh hủy toàn bộ luồng tự động đặt giá.");
    }

    // ── Đẩy sự kiện vào hàng đợi (Publish Event) ────────────────────────────

    /**
     * BẤT ĐỒNG BỘ: Điểm chạm để ClientHandler gọi xuống sau khi một User đặt giá tay thành công.
     * Hàm này chạy với tốc độ cực nhanh (Non-blocking) vì nó chỉ ném dữ liệu vào Queue rồi rút lui luôn.
     */
    public void publish(RecordBid event) {
        if (running) {
            queue.offer(event); // Ném nhẹ sự kiện đặt giá mới vào hàng đợi chờ xử lý
        }
    }

    // ── Vòng lặp xử lý sự kiện ngầm (Process Loop - Chạy trên Worker Thread) ──

    private void processLoop() {
        while (running) {
            try {
                // Đọc chặn (Blocking read): Luồng sẽ đứng ngủ tại đây nếu Queue rỗng.
                // Ngay khi có một người đặt giá mới, luồng sẽ tự thức giấc và bốc sự kiện ra xử lý.
                RecordBid event = queue.take();
                handleEvent(event);

            } catch (InterruptedException e) {
                // Khôi phục lại trạng thái ngắt luồng của hệ thống khi bị cưỡng chế dừng
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[❌] [BidAuto] Lỗi phát sinh trong vòng lặp xử lý sự kiện: " + e.getMessage());
            }
        }
    }

    // ── Xử lý chi tiết sự kiện đặt giá (Handle Event) ───────────────────────

    private void handleEvent(RecordBid event) {
        System.out.printf("[BidAuto Event Bus] Nhận tín hiệu đặt giá mới: Sản phẩm=%s, Người đặt=%s, Giá hiện tại=%.0f%n",
                event.productId(), event.bidderId(), event.newPrice());

        // BƯỚC 1: Gọi AutoBidService quét DB xem có các đối thủ nào cài đặt chế độ tự động nâng giá cho sản phẩm này không.
        // Bên trong AutoBidService của bạn sẽ tự động gọi dịch vụ đặt giá mới (BidPlacementService) để đẩy lệnh cược lên.
        AutoBidService.AutoBidResult result = autoBidService.triggerAutoBid(
                event.productId(),
                event.newPrice(),
                event.bidderId() // Truyền Id người vừa đặt để loại trừ, bot không tự đấu giá với chính mình
        );

        // BƯỚC 2: Nếu tìm thấy và kích nổ lệnh đặt giá tự động của bot thành công
        if (result != null && server != null) {
            System.out.printf("[🔥] [BidAuto] Bot trả giá thành công: Mã User=%s, Giá cược mới=%.0f VNĐ%n",
                    result.bidderId(), result.newBid());

            // Đóng gói Payload cập nhật giá tiền mới gửi về cho toàn bộ các Client đang mở phòng đấu giá xem
            String payload = gson.toJson(Map.of(
                    "productId",  event.productId(),
                    "newBid",     result.newBid(),
                    "bidderId",   result.bidderId(),
                    "bidderName", "[Tự động] " + result.bidderId(), // Đánh dấu nhãn bot tự động trên giao diện UI
                    "timestamp",  java.time.LocalDateTime.now().toString(),
                    "isAutoBid",  true
            ));

            // Phát sóng (Broadcast) bản tin cập nhật giá tới tất cả những ai đang xem sản phẩm này
            server.broadcastToAuction(
                    event.productId(),
                    new Message("BID_UPDATE", payload)
            );

            // BƯỚC 3: Nếu lượt cược tự động này rơi vào phút chót và kích hoạt cơ chế chống bắn tỉa phút cuối (Anti-sniping)
            if (result.newEndTime() != null) {
                String timePayload = gson.toJson(Map.of(
                        "productId",  event.productId(),
                        "newEndTime", result.newEndTime()
                ));
                // Bắn tin gia hạn thời gian phòng đấu giá sang phía Giao diện Client cập nhật lại đồng hồ đếm ngược
                server.broadcastToAuction(
                        event.productId(),
                        new Message("TIME_EXTENDED", timePayload)
                );
            }

            // BƯỚC 4: (TÍNH ĐỆ QUY CỰC HAY)
            // Lượt đặt giá tự động của Bot A vừa thành công, rất có thể nó sẽ chạm vào ngưỡng kích hoạt cấu hình tự động của Bot B!
            // Ta tự ném chính kết quả vừa rồi lại vào hàng đợi để tiếp tục kiểm tra chuỗi nâng giá tiếp theo.
            publish(new RecordBid(
                    event.productId(),
                    result.bidderId(),
                    result.newBid()
            ));
        }
    }
}