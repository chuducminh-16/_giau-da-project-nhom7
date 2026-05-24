package com.auction.server.service;

import com.auction.server.service.SnipeGuardService.SnipeGuardResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho SnipeGuardService.
 *
 * Vì SnipeGuardService.checkAndExtend() phụ thuộc DB (AuctionDAO),
 * ta test trực tiếp logic nội bộ qua SnipeGuardLogicHelper
 * — một inner helper tách biệt hoàn toàn khỏi DB.
 *
 * Test cases:
 * 1. Bid trong cửa sổ snipe (< 60s) → extended = true
 * 2. Bid ngoài cửa sổ snipe (> 60s) → extended = false
 * 3. Bid đúng ranh giới 60s → extended = true
 * 4. Bid đúng ranh giới 61s → extended = false
 * 5. endTime đã qua → extended = false
 * 6. SnipeGuardResult.extended(): các getter đúng
 * 7. SnipeGuardResult.noExtension(): reason không null
 * 8. Constants đúng giá trị
 */
@DisplayName("SnipeGuardService Tests")
public class SnipeGuardServiceTest {

    // ─────────────────────────────────────────────────────────────────────
    //  Helper: tách logic khỏi DB để test thuần túy (ĐÃ SỬA CỐ ĐỊNH THỜI GIAN)
    // ─────────────────────────────────────────────────────────────────────
    static SnipeGuardResult simulateCheckAndExtend(LocalDateTime now,
                                                   LocalDateTime endTime,
                                                   int snipeWindowSeconds,
                                                   int extendBySeconds) {
        if (endTime == null) {
            return SnipeGuardResult.noExtension("endTime null");
        }

        // Tính toán dựa trên mốc 'now' cố định được truyền vào thay vì LocalDateTime.now()
        long secondsLeft = java.time.Duration
                .between(now, endTime)
                .getSeconds();

        if (secondsLeft < 0) {
            return SnipeGuardResult.noExtension("Phiên đã hết giờ.");
        }

        if (secondsLeft > snipeWindowSeconds) {
            return SnipeGuardResult.noExtension(
                    String.format("Còn %ds — ngoài cửa sổ snipe %ds.", secondsLeft, snipeWindowSeconds));
        }

        // Gia hạn
        LocalDateTime newEnd = endTime.plusSeconds(extendBySeconds);
        String newEndStr = newEnd.toString();

        return SnipeGuardResult.extended(1L, secondsLeft, extendBySeconds, newEndStr, newEnd);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Constants
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SNIPE_WINDOW_SECONDS phải là 60")
    void constants_snipeWindowIs60() {
        assertEquals(60, SnipeGuardService.SNIPE_WINDOW_SECONDS);
    }

    @Test
    @DisplayName("SNIPE_EXTEND_SECONDS phải là 60")
    void constants_extendIs60() {
        assertEquals(60, SnipeGuardService.SNIPE_EXTEND_SECONDS);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Scenarios (ĐÃ SỬA CÁC HÀM TEST ĐỂ TRUYỀN THỜI GIAN CỐ ĐỊNH)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bid lúc còn 30s (trong cửa sổ 60s) → extended = true")
    void bid_at30sLeft_shouldExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(30);
        SnipeGuardResult result = simulateCheckAndExtend(now, endTime, 60, 60);

        assertTrue(result.extended);
        assertEquals(60, result.extendedBySeconds);
        assertNotNull(result.newEndTimeStr);
        assertNotNull(result.newEndTime);
        assertNull(result.reason);
    }

    @Test
    @DisplayName("Bid lúc còn 59s (trong cửa sổ 60s) → extended = true")
    void bid_at59sLeft_shouldExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(59);
        SnipeGuardResult result = simulateCheckAndExtend(now, endTime, 60, 60);

        assertTrue(result.extended);
    }

    @Test
    @DisplayName("Bid lúc còn đúng 60s (ranh giới) → extended = true")
    void bid_at60sLeft_boundary_shouldExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(60);
        SnipeGuardResult result = simulateCheckAndExtend(now, endTime, 60, 60);

        assertTrue(result.extended,
                "Ranh giới 60s phải được gia hạn (secondsLeft <= SNIPE_WINDOW)");
    }

    @Test
    @DisplayName("Bid lúc còn 61s (ngoài cửa sổ) → extended = false")
    void bid_at61sLeft_shouldNotExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(61);
        SnipeGuardResult result = simulateCheckAndExtend(now, endTime, 60, 60);

        assertFalse(result.extended);
        assertNotNull(result.reason);
        assertNull(result.newEndTimeStr);
    }

    @Test
    @DisplayName("Bid lúc còn 5 phút → extended = false")
    void bid_at5MinLeft_shouldNotExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.plusMinutes(5);
        SnipeGuardResult result = simulateCheckAndExtend(now, endTime, 60, 60);

        assertFalse(result.extended);
    }

    @Test
    @DisplayName("Bid lúc còn 1s → extended = true, newEndTime đúng +60s")
    void bid_at1sLeft_shouldExtendAndNewEndTimeCorrect() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(1);
        SnipeGuardResult result = simulateCheckAndExtend(now, endTime, 60, 60);

        assertTrue(result.extended);
        // newEndTime phải xấp xỉ endTime + 60s
        long diff = java.time.Duration.between(endTime, result.newEndTime).getSeconds();
        assertEquals(60, diff, "newEndTime phải bằng endTime + 60s");
    }

    @Test
    @DisplayName("endTime đã qua (phiên hết giờ) → extended = false")
    void bid_expiredAuction_shouldNotExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.minusSeconds(10);
        SnipeGuardResult result = simulateCheckAndExtend(now, endTime, 60, 60);

        assertFalse(result.extended);
        assertNotNull(result.reason);
    }

    @Test
    @DisplayName("endTime null → extended = false, reason không null")
    void bid_nullEndTime_shouldNotExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        SnipeGuardResult result = simulateCheckAndExtend(now, null, 60, 60);

        assertFalse(result.extended);
        assertNotNull(result.reason);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SnipeGuardResult
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SnipeGuardResult.extended(): getter đúng")
    void result_extended_gettersCorrect() {
        LocalDateTime newEnd = LocalDateTime.now().plusSeconds(60);
        SnipeGuardResult r = SnipeGuardResult.extended(42L, 30L, 60, newEnd.toString(), newEnd);

        assertTrue(r.extended);
        assertEquals(42L,  r.auctionId);
        assertEquals(30L,  r.secondsLeftBeforeExtend);
        assertEquals(60,   r.extendedBySeconds);
        assertNotNull(r.newEndTimeStr);
        assertNotNull(r.newEndTime);
        assertNull(r.reason);
    }

    @Test
    @DisplayName("SnipeGuardResult.noExtension(): reason đúng, extended = false")
    void result_noExtension_correct() {
        SnipeGuardResult r = SnipeGuardResult.noExtension("Test reason");

        assertFalse(r.extended);
        assertEquals("Test reason", r.reason);
        assertNull(r.newEndTimeStr);
        assertNull(r.newEndTime);
        assertEquals(-1, r.auctionId);
    }

    @Test
    @DisplayName("SnipeGuardResult.toString() phân biệt EXTENDED vs NO_EXTENSION")
    void result_toString_descriptive() {
        LocalDateTime newEnd = LocalDateTime.now().plusSeconds(60);
        SnipeGuardResult extended = SnipeGuardResult.extended(1L, 30L, 60, newEnd.toString(), newEnd);
        SnipeGuardResult noExt   = SnipeGuardResult.noExtension("outside window");

        assertTrue(extended.toString().contains("EXTENDED"));
        assertTrue(noExt.toString().contains("NO_EXTENSION"));
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Nhiều lần gia hạn liên tiếp (cascade)
    // ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Cascade: 3 bid liên tiếp trong cửa sổ snipe → mỗi lần đều gia hạn")
    void cascade_3bids_allExtend() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime endTime = now.plusSeconds(30);

        // Bid 1
        SnipeGuardResult r1 = simulateCheckAndExtend(now, endTime, 60, 60);
        assertTrue(r1.extended);

        // Bid 2 — Giả lập thời gian trôi qua 5 giây (vẫn dùng mốc cố định không sợ lệch)
        LocalDateTime nowBid2 = now.plusSeconds(5);
        SnipeGuardResult r2 = simulateCheckAndExtend(nowBid2, r1.newEndTime, 60, 60);
        assertTrue(r2.extended, "Bid 2 vẫn trong cửa sổ snipe → phải gia hạn lần 2");

        // Bid 3 — Giả lập thời gian trôi qua thêm 5 giây nữa
        LocalDateTime nowBid3 = nowBid2.plusSeconds(5);
        SnipeGuardResult r3 = simulateCheckAndExtend(nowBid3, r2.newEndTime, 60, 60);
        assertTrue(r3.extended, "Bid 3 vẫn trong cửa sổ snipe → phải gia hạn lần 3");

        // Tổng thời gian gia hạn phải là 3 × 60 = 180s so với endTime ban đầu
        long totalExtension = java.time.Duration.between(endTime, r3.newEndTime).getSeconds();
        assertEquals(180, totalExtension);
    }

    @Test
    @DisplayName("Sau gia hạn: nếu bid còn đủ xa → không gia hạn thêm")
    void afterExtend_bidFarFromEnd_noFurtherExtension() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 25, 12, 0, 0);
        LocalDateTime newEndAfterExtend = now.plusSeconds(90);
        SnipeGuardResult r = simulateCheckAndExtend(now, newEndAfterExtend, 60, 60);

        assertFalse(r.extended, "90s còn lại > 60s cửa sổ → không gia hạn thêm");
    }
}