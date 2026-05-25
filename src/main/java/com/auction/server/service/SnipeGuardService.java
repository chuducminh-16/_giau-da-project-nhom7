package com.auction.server.service;

import com.auction.server.dao.auction.AuctionDAO;
import com.auction.server.dao.bid.BidDAO;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SnipeGuardService — Chức năng chống sniping (đặt giá vào giây chót).
 *
 * ═══════════════════════════════════════════════════════════════════
 *  CÁCH HOẠT ĐỘNG:
 *  - Khi có bid hợp lệ trong 60 giây cuối của phiên
 *    → Server tự động gia hạn thêm 60 giây
 *  - Việc gia hạn được broadcast đến TẤT CẢ client đang xem phiên
 *    (message type: AUCTION_EXTENDED)
 *  - Client (LiveBiddingController) nhận AUCTION_EXTENDED và restart
 *    đồng hồ đếm ngược với thời gian mới
 * ═══════════════════════════════════════════════════════════════════
 *
 *  FILE MỚI — không sửa AuctionService.java gốc.
 *  AuctionController (server) gọi SnipeGuardService.checkAndExtend()
 *  ngay sau khi placeBid() thành công.
 */
public class SnipeGuardService {

    // Cửa sổ snipe: nếu bid trong N giây cuối → gia hạn
    public static final int SNIPE_WINDOW_SECONDS = 60;

    // Số giây gia hạn mỗi lần
    public static final int SNIPE_EXTEND_SECONDS = 60;

    private final AuctionDAO auctionDAO = new AuctionDAO();
    private final BidDAO     bidDAO     = new BidDAO();

    // Fair lock per auctionId để tránh race condition khi nhiều bid cùng trigger gia hạn
    private final ConcurrentHashMap<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private ReentrantLock getLock(long auctionId) {
        return lockMap.computeIfAbsent(auctionId, id -> new ReentrantLock(true));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  checkAndExtend()
    //  Gọi sau khi placeBid() thành công.
    //  Trả về SnipeGuardResult:
    //    - extended = true  → đã gia hạn, newEndTime chứa thời điểm kết thúc mới
    //    - extended = false → không cần gia hạn (còn nhiều giờ)
    // ─────────────────────────────────────────────────────────────────────
    public SnipeGuardResult checkAndExtend(long auctionId) {
        ReentrantLock lock = getLock(auctionId);
        lock.lock();
        try {
            // Lấy endTime hiện tại từ DB (sau khi bid đã được lưu)
            String endTimeStr = auctionDAO.getEndTime(auctionId);
            if (endTimeStr == null) {
                return SnipeGuardResult.noExtension("Không tìm thấy endTime của phiên #" + auctionId);
            }

            LocalDateTime endTime = parseDateTime(endTimeStr);
            if (endTime == null) {
                return SnipeGuardResult.noExtension("Không parse được endTime: " + endTimeStr);
            }

            long secondsLeft = java.time.Duration
                    .between(LocalDateTime.now(), endTime)
                    .getSeconds();

            // Kiểm tra có nằm trong cửa sổ snipe không
            if (secondsLeft < 0) {
                // Phiên đã hết giờ trước bid này (edge case)
                return SnipeGuardResult.noExtension("Phiên đã hết giờ trước khi kiểm tra snipe.");
            }

            if (secondsLeft > SNIPE_WINDOW_SECONDS) {
                // Còn nhiều thời gian, không cần gia hạn
                return SnipeGuardResult.noExtension(
                        String.format("Còn %d giây — ngoài cửa sổ snipe %ds.",
                                secondsLeft, SNIPE_WINDOW_SECONDS));
            }

            // ✅ Nằm trong cửa sổ snipe → gia hạn
            boolean extended = auctionDAO.extendEndTime(auctionId, SNIPE_EXTEND_SECONDS);
            if (!extended) {
                return SnipeGuardResult.noExtension("extendEndTime() thất bại cho phiên #" + auctionId);
            }

            // Lấy endTime mới sau khi đã gia hạn
            String newEndTimeStr = auctionDAO.getEndTime(auctionId);
            LocalDateTime newEndTime = newEndTimeStr != null ? parseDateTime(newEndTimeStr) : null;

            System.out.printf("[SnipeGuard] Phiên #%d còn %ds → GIA HẠN +%ds → kết thúc lúc %s%n",
                    auctionId, secondsLeft, SNIPE_EXTEND_SECONDS, newEndTimeStr);

            return SnipeGuardResult.extended(
                    auctionId,
                    secondsLeft,
                    SNIPE_EXTEND_SECONDS,
                    newEndTimeStr,
                    newEndTime
            );

        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Helper: parse datetime string từ DB (hỗ trợ cả "T" và " " separator)
    // ─────────────────────────────────────────────────────────────────────
    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDateTime.parse(str.replace(" ", "T"));
        } catch (Exception e) {
            System.err.println("[SnipeGuard] Lỗi parse datetime: " + str + " → " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SnipeGuardResult — immutable, trả về từ checkAndExtend()
    // ─────────────────────────────────────────────────────────────────────
    public static final class SnipeGuardResult {

        /** true nếu đã gia hạn thành công */
        public final boolean       extended;

        /** ID phiên (chỉ set khi extended = true) */
        public final long          auctionId;

        /** Số giây còn lại tại thời điểm bid (trước khi gia hạn) */
        public final long          secondsLeftBeforeExtend;

        /** Số giây đã gia hạn thêm */
        public final int           extendedBySeconds;

        /** Thời điểm kết thúc mới dạng String (để gửi qua socket) */
        public final String        newEndTimeStr;

        /** Thời điểm kết thúc mới dạng LocalDateTime */
        public final LocalDateTime newEndTime;

        /** Lý do không gia hạn (chỉ set khi extended = false) */
        public final String        reason;

        private SnipeGuardResult(boolean extended, long auctionId,
                                  long secondsLeft, int extendedBy,
                                  String newEndTimeStr, LocalDateTime newEndTime,
                                  String reason) {
            this.extended               = extended;
            this.auctionId              = auctionId;
            this.secondsLeftBeforeExtend = secondsLeft;
            this.extendedBySeconds      = extendedBy;
            this.newEndTimeStr          = newEndTimeStr;
            this.newEndTime             = newEndTime;
            this.reason                 = reason;
        }

        /** Tạo kết quả gia hạn thành công */
        static SnipeGuardResult extended(long auctionId, long secondsLeft,
                                          int extendedBy, String newEndTimeStr,
                                          LocalDateTime newEndTime) {
            return new SnipeGuardResult(true, auctionId, secondsLeft,
                    extendedBy, newEndTimeStr, newEndTime, null);
        }

        /** Tạo kết quả không gia hạn */
        static SnipeGuardResult noExtension(String reason) {
            return new SnipeGuardResult(false, -1, 0, 0, null, null, reason);
        }

        @Override
        public String toString() {
            if (extended) {
                return String.format("SnipeGuardResult{EXTENDED, auctionId=%d, " +
                        "wasLeft=%ds, addedBy=%ds, newEnd=%s}",
                        auctionId, secondsLeftBeforeExtend, extendedBySeconds, newEndTimeStr);
            }
            return "SnipeGuardResult{NO_EXTENSION, reason='" + reason + "'}";
        }
    }
}
