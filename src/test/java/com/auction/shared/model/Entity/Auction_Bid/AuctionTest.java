package com.auction.shared.model.Entity.Auction_Bid;

import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.User.Seller;
import com.auction.shared.pattern.AuctionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho Auction, Bid và AuctionManager (Singleton).
 *
 * Kiểm tra:
 *  - Auction: trạng thái OPEN/RUNNING/FINISHED theo thời gian
 *  - isActive() đúng logic
 *  - Bid: constructor, getter
 *  - AuctionManager: Singleton, add/remove/findById
 */
@DisplayName("Auction & AuctionManager Tests")
public class AuctionTest {

    private Art    item;
    private Seller seller;

    @BeforeEach
    void setUp() {
        item   = new Art("item-1", "Test Art", 1000.0,
                LocalDateTime.now().plusDays(1), "s1", "TestArtist");
        seller = new Seller("s1", "sellerUser", "s@mail.com", "pass", 5.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUCTION STATUS
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Auction mới tạo → status là OPEN")
    void auction_newAuction_statusIsOpen() {
        Auction a = new Auction(1L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        assertEquals(AuctionStatus.OPEN, a.getStatus());
    }

    @Test
    @DisplayName("Auction đang chạy (startTime quá khứ, endTime tương lai) → isActive true")
    void auction_running_isActiveTrue() {
        Auction a = new Auction(2L, item, seller, 1000.0,
                LocalDateTime.now().minusHours(1),   // start = 1h trước
                LocalDateTime.now().plusHours(2));   // end   = 2h sau
        assertTrue(a.isActive());
    }

    @Test
    @DisplayName("Auction đã hết giờ (endTime quá khứ) → isActive false")
    void auction_expired_isActiveFalse() {
        Auction a = new Auction(3L, item, seller, 1000.0,
                LocalDateTime.now().minusDays(2),   // start = 2 ngày trước
                LocalDateTime.now().minusMinutes(5)); // end = 5 phút trước
        assertFalse(a.isActive());
    }

    @Test
    @DisplayName("Auction đã hết giờ → refreshStatus() → status FINISHED")
    void auction_expired_refreshStatus_FINISHED() {
        Auction a = new Auction(3L, item, seller, 1000.0,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusMinutes(5));
        a.refreshStatus();
        assertEquals(AuctionStatus.FINISHED, a.getStatus());
    }

    @Test
    @DisplayName("Auction CANCELED → refreshStatus() không thay đổi trạng thái")
    void auction_canceled_refreshStatus_unchanged() {
        Auction a = new Auction(4L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        a.setStatus(AuctionStatus.CANCELED);
        a.refreshStatus(); // Không được thay đổi
        assertEquals(AuctionStatus.CANCELED, a.getStatus());
    }

    @Test
    @DisplayName("Auction PAID → refreshStatus() không thay đổi trạng thái")
    void auction_paid_refreshStatus_unchanged() {
        Auction a = new Auction(5L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        a.setStatus(AuctionStatus.PAID);
        a.refreshStatus();
        assertEquals(AuctionStatus.PAID, a.getStatus());
    }

    @Test
    @DisplayName("setCurrentPrice() cập nhật giá đúng")
    void auction_setCurrentPrice_updated() {
        Auction a = new Auction(1L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        a.setCurrentPrice(2500.0);
        assertEquals(2500.0, a.getCurrentPrice(), 0.001);
    }

    @Test
    @DisplayName("setLeadingBidderId() cập nhật bidder dẫn đầu")
    void auction_setLeadingBidderId_updated() {
        Auction a = new Auction(1L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        a.setLeadingBidderId("bidder-99");
        assertEquals("bidder-99", a.getLeadingBidderId());
    }

    @Test
    @DisplayName("setStatus(String) từ DB → chuyển đúng enum")
    void auction_setStatusString_convertsToEnum() {
        Auction a = new Auction(1L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        a.setStatus("RUNNING");
        assertEquals(AuctionStatus.RUNNING, a.getStatus());
    }

    @Test
    @DisplayName("setStatus(String) giá trị lạ → fallback về OPEN")
    void auction_setStatusInvalidString_fallbackOPEN() {
        Auction a = new Auction(1L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        a.setStatus("GARBAGE_VALUE");
        assertEquals(AuctionStatus.OPEN, a.getStatus());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BID
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Bid: constructor lưu đúng các trường")
    void bid_constructor_fieldsCorrect() {
        Bid bid = new Bid("bid-1", "item-1", "bidder-1", 1500.0);
        assertEquals("bid-1",    bid.getId());
        assertEquals("item-1",   bid.getItemId());
        assertEquals("bidder-1", bid.getBidderId());
        assertEquals(1500.0,     bid.getAmount(), 0.001);
        assertNotNull(bid.getTimestamp());
    }

    @Test
    @DisplayName("Bid: timestamp tự động gán thời điểm hiện tại")
    void bid_timestamp_notNull() {
        Bid bid = new Bid("b1", "i1", "u1", 999.0);
        assertNotNull(bid.getTimestamp());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // AUCTION MANAGER (Singleton Pattern)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AuctionManager: Singleton → cùng instance")
    void auctionManager_singleton_sameInstance() {
        AuctionManager m1 = AuctionManager.getInstance();
        AuctionManager m2 = AuctionManager.getInstance();
        assertSame(m1, m2);
    }

    @Test
    @DisplayName("AuctionManager: addAuction → findById tìm được")
    void auctionManager_addAndFindById() {
        AuctionManager mgr = AuctionManager.getInstance();
        Auction a = new Auction(9001L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        mgr.addAuction(a);

        Optional<Auction> found = mgr.findById(9001L);
        assertTrue(found.isPresent());
        assertEquals(9001L, found.get().getId());

        // Cleanup
        mgr.removeAuction(9001L);
    }

    @Test
    @DisplayName("AuctionManager: removeAuction → findById không tìm thấy")
    void auctionManager_removeAuction_notFoundAfter() {
        AuctionManager mgr = AuctionManager.getInstance();
        Auction a = new Auction(9002L, item, seller, 1000.0,
                LocalDateTime.now().plusDays(1));
        mgr.addAuction(a);
        mgr.removeAuction(9002L);

        Optional<Auction> found = mgr.findById(9002L);
        assertFalse(found.isPresent());
    }

    @Test
    @DisplayName("AuctionManager: getActiveAuctions chỉ trả auction isActive()")
    void auctionManager_getActiveAuctions_onlyActive() {
        AuctionManager mgr = AuctionManager.getInstance();

        // Active auction
        Auction active = new Auction(9010L, item, seller, 1000.0,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(2));
        // Expired auction
        Auction expired = new Auction(9011L, item, seller, 1000.0,
                LocalDateTime.now().minusDays(2),
                LocalDateTime.now().minusMinutes(10));

        mgr.addAuction(active);
        mgr.addAuction(expired);

        long count = mgr.getActiveAuctions().stream()
                .filter(a -> a.getId() == 9010L || a.getId() == 9011L)
                .count();

        // Chỉ auction 9010 là active
        assertTrue(mgr.getActiveAuctions().stream()
                .anyMatch(a -> a.getId() == 9010L));
        assertFalse(mgr.getActiveAuctions().stream()
                .anyMatch(a -> a.getId() == 9011L));

        // Cleanup
        mgr.removeAuction(9010L);
        mgr.removeAuction(9011L);
    }

    @Test
    @DisplayName("AuctionManager: findByItemName tìm đúng tên (case-insensitive)")
    void auctionManager_findByItemName_found() {
        AuctionManager mgr = AuctionManager.getInstance();
        Art artItem = new Art("item-99", "Sunflowers", 2000.0,
                LocalDateTime.now().plusDays(1), "s1", "Van Gogh");
        Auction a = new Auction(9020L, artItem, seller, 2000.0,
                LocalDateTime.now().plusDays(1));
        mgr.addAuction(a);

        Optional<Auction> found = mgr.findByItemName("sunflowers"); // lowercase
        assertTrue(found.isPresent());

        // Cleanup
        mgr.removeAuction(9020L);
    }
}
