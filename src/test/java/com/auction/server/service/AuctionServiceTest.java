package com.auction.server.service;

import com.auction.server.service.AuctionService.BidOutcome;
import com.auction.server.service.AuctionService.BidResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho AuctionService.
 *
 * NOTE: AuctionService dùng DAO → DB nên ta KHÔNG test trực tiếp placeBid()
 * (cần DB thật). Thay vào đó ta test:
 *  1. BidOutcome / BidResult record/enum hoạt động đúng
 *  2. Logic validate bid (giá thấp hơn / phiên đã đóng) qua mock inline
 *  3. Concurrency: nhiều thread đặt giá cùng lúc → chỉ 1 winner (race condition test)
 *
 * BidOutcome có 4 trường: result, newBid, message, newEndTime (anti-sniping).
 */
@DisplayName("AuctionService Logic Tests")
public class AuctionServiceTest {

    @BeforeEach
    void setUp() {}

    // ─────────────────────────────────────────────────────────────────────────
    // BidResult enum
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BidResult có đầy đủ các giá trị enum cần thiết")
    void bidResult_enum_hasRequiredValues() {
        BidResult[] values = BidResult.values();
        List<String> names = new ArrayList<>();
        for (BidResult v : values) names.add(v.name());

        assertTrue(names.contains("SUCCESS"));
        assertTrue(names.contains("PRICE_TOO_LOW"));
        assertTrue(names.contains("AUCTION_ENDED"));
        assertTrue(names.contains("AUCTION_NOT_FOUND"));
        assertTrue(names.contains("ERROR"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BidOutcome record
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BidOutcome: getter trả đúng result, newBid, message, newEndTime=null")
    void bidOutcome_getters_correct() {
        BidOutcome outcome = new BidOutcome(BidResult.SUCCESS, 5000.0, "Đặt giá thành công!", null);
        assertEquals(BidResult.SUCCESS, outcome.result());
        assertEquals(5000.0,            outcome.newBid(), 0.001);
        assertEquals("Đặt giá thành công!", outcome.message());
        assertNull(outcome.newEndTime()); // không gia hạn → null
    }

    @Test
    @DisplayName("BidOutcome với newEndTime != null (anti-sniping đã gia hạn)")
    void bidOutcome_withNewEndTime_notNull() {
        String extendedTime = LocalDateTime.now().plusSeconds(60).toString();
        BidOutcome outcome = new BidOutcome(BidResult.SUCCESS, 5000.0, "Đặt giá thành công!", extendedTime);
        assertNotNull(outcome.newEndTime());
    }

    @Test
    @DisplayName("BidOutcome PRICE_TOO_LOW: result đúng")
    void bidOutcome_priceTooLow_resultCorrect() {
        BidOutcome outcome = new BidOutcome(BidResult.PRICE_TOO_LOW, 3000.0, "Giá quá thấp", null);
        assertEquals(BidResult.PRICE_TOO_LOW, outcome.result());
        assertEquals(3000.0, outcome.newBid(), 0.001);
    }

    @Test
    @DisplayName("BidOutcome AUCTION_ENDED: result đúng")
    void bidOutcome_auctionEnded_resultCorrect() {
        BidOutcome outcome = new BidOutcome(BidResult.AUCTION_ENDED, 0, "Phiên đã kết thúc", null);
        assertEquals(BidResult.AUCTION_ENDED, outcome.result());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BID VALIDATION LOGIC (Inline mock - không cần DB)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Giả lập logic validate bid của AuctionService.placeBid() không có DB.
     * newEndTime luôn null ở đây vì mock không tính anti-sniping.
     */
    static BidOutcome validateBid(Map<String, Object> auction, double amount) {
        if (auction == null)
            return new BidOutcome(BidResult.AUCTION_NOT_FOUND, 0, "Không tìm thấy phiên.", null);

        String status = (String) auction.get("status");
        if (!"OPEN".equals(status) && !"RUNNING".equals(status))
            return new BidOutcome(BidResult.AUCTION_ENDED, 0, "Phiên đã kết thúc.", null);

        String endTimeStr = (String) auction.get("endTime");
        if (endTimeStr != null) {
            LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
            if (LocalDateTime.now().isAfter(endTime))
                return new BidOutcome(BidResult.AUCTION_ENDED, 0, "Phiên đã hết giờ.", null);
        }

        double currentPrice = (double) auction.get("currentPrice");
        if (amount <= currentPrice)
            return new BidOutcome(BidResult.PRICE_TOO_LOW, currentPrice,
                    String.format("Giá phải cao hơn %.0f VND.", currentPrice), null);

        return new BidOutcome(BidResult.SUCCESS, amount, "Đặt giá thành công!", null);
    }

    @Test
    @DisplayName("Bid hợp lệ cao hơn giá hiện tại → SUCCESS")
    void validateBid_validAmount_returnsSuccess() {
        Map<String, Object> auction = mockAuction("RUNNING", 1000.0, "+1d");
        BidOutcome result = validateBid(auction, 2000.0);
        assertEquals(BidResult.SUCCESS, result.result());
        assertEquals(2000.0, result.newBid(), 0.001);
        assertNull(result.newEndTime()); // mock không có anti-sniping
    }

    @Test
    @DisplayName("Bid bằng giá hiện tại → PRICE_TOO_LOW")
    void validateBid_equalToCurrentPrice_returnsPriceTooLow() {
        Map<String, Object> auction = mockAuction("RUNNING", 1000.0, "+1d");
        BidOutcome result = validateBid(auction, 1000.0);
        assertEquals(BidResult.PRICE_TOO_LOW, result.result());
    }

    @Test
    @DisplayName("Bid thấp hơn giá hiện tại → PRICE_TOO_LOW")
    void validateBid_belowCurrentPrice_returnsPriceTooLow() {
        Map<String, Object> auction = mockAuction("RUNNING", 5000.0, "+1d");
        BidOutcome result = validateBid(auction, 3000.0);
        assertEquals(BidResult.PRICE_TOO_LOW, result.result());
        assertEquals(5000.0, result.newBid(), 0.001); // trả giá hiện tại
    }

    @Test
    @DisplayName("Phiên đã đóng (FINISHED) → AUCTION_ENDED")
    void validateBid_auctionFinished_returnsAuctionEnded() {
        Map<String, Object> auction = mockAuction("FINISHED", 1000.0, "-1d");
        BidOutcome result = validateBid(auction, 9999.0);
        assertEquals(BidResult.AUCTION_ENDED, result.result());
    }

    @Test
    @DisplayName("Phiên đã hết giờ (endTime quá khứ) → AUCTION_ENDED")
    void validateBid_expiredEndTime_returnsAuctionEnded() {
        Map<String, Object> auction = mockAuction("RUNNING", 1000.0, "-1h");
        BidOutcome result = validateBid(auction, 9999.0);
        assertEquals(BidResult.AUCTION_ENDED, result.result());
    }

    @Test
    @DisplayName("auction null (không tìm thấy) → AUCTION_NOT_FOUND")
    void validateBid_nullAuction_returnsNotFound() {
        BidOutcome result = validateBid(null, 9999.0);
        assertEquals(BidResult.AUCTION_NOT_FOUND, result.result());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ANTI-SNIPING LOGIC (mock, không cần DB)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Giả lập logic anti-sniping: nếu bid trong 60 giây cuối → gia hạn thêm 60 giây.
     */
    static BidOutcome validateBidWithAntiSniping(Map<String, Object> auction, double amount) {
        BidOutcome base = validateBid(auction, amount);
        if (base.result() != BidResult.SUCCESS) return base;

        String endTimeStr = (String) auction.get("endTime");
        if (endTimeStr == null) return base;

        LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
        long secondsLeft = java.time.Duration.between(LocalDateTime.now(), endTime).getSeconds();

        String newEndTime = null;
        if (secondsLeft <= 60) {
            newEndTime = endTime.plusSeconds(60).toString();
        }
        return new BidOutcome(BidResult.SUCCESS, amount, "Đặt giá thành công!", newEndTime);
    }

    @Test
    @DisplayName("Anti-sniping: bid trong 60 giây cuối → newEndTime != null (gia hạn)")
    void antiSniping_bidInLastMinute_extendsEndTime() {
        // endTime = 30 giây nữa (trong cửa sổ 60 giây)
        Map<String, Object> auction = new HashMap<>();
        auction.put("status", "RUNNING");
        auction.put("currentPrice", 1000.0);
        auction.put("endTime", LocalDateTime.now().plusSeconds(30).toString());

        BidOutcome result = validateBidWithAntiSniping(auction, 2000.0);
        assertEquals(BidResult.SUCCESS, result.result());
        assertNotNull(result.newEndTime(), "Phải gia hạn khi bid trong 60s cuối");
    }

    @Test
    @DisplayName("Anti-sniping: bid trước cửa sổ snipe → newEndTime == null (không gia hạn)")
    void antiSniping_bidBeforeSnipeWindow_noExtension() {
        // endTime = 120 giây nữa (ngoài cửa sổ 60 giây)
        Map<String, Object> auction = new HashMap<>();
        auction.put("status", "RUNNING");
        auction.put("currentPrice", 1000.0);
        auction.put("endTime", LocalDateTime.now().plusSeconds(120).toString());

        BidOutcome result = validateBidWithAntiSniping(auction, 2000.0);
        assertEquals(BidResult.SUCCESS, result.result());
        assertNull(result.newEndTime(), "Không gia hạn khi còn nhiều thời gian");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONCURRENCY: nhiều thread đặt giá → không có race condition
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Mô phỏng AuctionService.placeBid() thread-safe với ReentrantLock.
     */
    static class SafeBidProcessor {
        private double currentPrice;
        private final java.util.concurrent.locks.ReentrantLock lock =
                new java.util.concurrent.locks.ReentrantLock();
        private final List<Double> acceptedBids = new CopyOnWriteArrayList<>();

        SafeBidProcessor(double startPrice) {
            this.currentPrice = startPrice;
        }

        BidResult bid(double amount) {
            lock.lock();
            try {
                if (amount <= currentPrice) return BidResult.PRICE_TOO_LOW;
                currentPrice = amount;
                acceptedBids.add(amount);
                return BidResult.SUCCESS;
            } finally {
                lock.unlock();
            }
        }

        double getCurrentPrice() { return currentPrice; }
        List<Double> getAcceptedBids() { return acceptedBids; }
    }

    @Test
    @DisplayName("Concurrent Bidding: nhiều thread đặt giá → chỉ giá cao nhất được chấp nhận")
    void concurrentBidding_noLostUpdate() throws InterruptedException {
        SafeBidProcessor processor = new SafeBidProcessor(1000.0);
        int threadCount = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 1; i <= threadCount; i++) {
            final double bid = 1000.0 + i * 100.0;
            pool.submit(() -> {
                processor.bid(bid);
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        double finalPrice = processor.getCurrentPrice();
        assertTrue(finalPrice >= 1100.0, "Giá cuối phải >= giá bid đầu tiên");

        List<Double> accepted = processor.getAcceptedBids();
        for (int i = 1; i < accepted.size(); i++) {
            assertTrue(accepted.get(i) > accepted.get(i - 1),
                    "Giá trong danh sách accepted phải tăng dần");
        }
    }

    @Test
    @DisplayName("Concurrent Bidding: chỉ 1 trong nhiều thread cùng giá được chấp nhận")
    void concurrentBidding_samePriceOnlyOneAccepted() throws InterruptedException {
        SafeBidProcessor processor = new SafeBidProcessor(1000.0);
        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                processor.bid(1500.0);
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, processor.getAcceptedBids().size(),
                "Khi nhiều thread cùng đặt giá 1500, chỉ 1 được chấp nhận");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private Map<String, Object> mockAuction(String status, double currentPrice, String timeOffset) {
        Map<String, Object> a = new HashMap<>();
        a.put("status", status);
        a.put("currentPrice", currentPrice);

        LocalDateTime endTime;
        if (timeOffset.startsWith("+")) {
            int val = Integer.parseInt(timeOffset.replaceAll("[^0-9]", ""));
            endTime = timeOffset.contains("d")
                    ? LocalDateTime.now().plusDays(val)
                    : LocalDateTime.now().plusHours(val);
        } else {
            int val = Integer.parseInt(timeOffset.replaceAll("[^0-9]", ""));
            endTime = timeOffset.contains("d")
                    ? LocalDateTime.now().minusDays(val)
                    : LocalDateTime.now().minusHours(val);
        }
        a.put("endTime", endTime.toString());
        return a;
    }
}
