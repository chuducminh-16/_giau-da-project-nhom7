package com.auction.shared.pattern.observer;

import com.auction.shared.model.Entity.Auction_Bid.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit Test cho Observer Pattern.
 *
 * Các class liên quan (cùng package com.auction.shared.pattern.observer):
 *   - AuctionObserver  (interface)
 *   - AuctionSubject   (abstract class)
 *   - BidEvent         (immutable data)
 *
 * Vì AuctionSubject là abstract, ta tạo TestSubject kế thừa nó
 * để expose 2 method protected ra ngoài cho test gọi.
 */
@DisplayName("Observer Pattern – AuctionSubject / AuctionObserver / BidEvent")
public class ObserverPatternTest {

    // ══════════════════════════════════════════════════════════════════════
    //  HELPER CLASSES (inner static – không cần file riêng)
    // ══════════════════════════════════════════════════════════════════════

    /** Concrete Subject – chỉ mục đích test, expose 2 method protected. */
    static class TestSubject extends AuctionSubject {
        public void fireBidPlaced(BidEvent event) {
            notifyBidPlaced(event);           // protected → gọi được từ subclass
        }
        public void fireAuctionClosed(long auctionId, String winnerId, double price) {
            notifyAuctionClosed(auctionId, winnerId, price);
        }
    }

    /** Observer ghi lại mọi thứ nó nhận được để assert. */
    static class RecordingObserver implements AuctionObserver {
        int    bidCallCount   = 0;
        int    closeCallCount = 0;
        double lastNewPrice   = -1;
        String lastWinnerId   = null;

        @Override
        public void onBidPlaced(BidEvent event) {
            bidCallCount++;
            lastNewPrice = event.getNewPrice();
        }

        @Override
        public void onAuctionClosed(long auctionId, String winnerId, double finalPrice) {
            closeCallCount++;
            lastWinnerId = winnerId;
            lastNewPrice = finalPrice;
        }
    }

    /** Observer cố tình throw exception – để kiểm tra isolation. */
    static class BrokenObserver implements AuctionObserver {
        @Override public void onBidPlaced(BidEvent event) {
            throw new RuntimeException("Tôi bị lỗi!");
        }
        @Override public void onAuctionClosed(long id, String w, double p) {
            throw new RuntimeException("Tôi bị lỗi khi close!");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SETUP
    // ══════════════════════════════════════════════════════════════════════

    private TestSubject       subject;
    private RecordingObserver obs1;
    private RecordingObserver obs2;
    private BidEvent          sampleEvent;

    @BeforeEach
    void setUp() {
        subject = new TestSubject();
        obs1    = new RecordingObserver();
        obs2    = new RecordingObserver();

        // Tạo BidEvent mẫu để dùng trong các test
        Bid bid = new Bid("bid-001", "item-001", "bidder-001", 2000.0);
        sampleEvent = new BidEvent(1L, "item-001", bid, 2000.0, "Alice");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SUBSCRIBE / UNSUBSCRIBE
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Ban đầu chưa có observer nào → count = 0")
    void initialObserverCount_isZero() {
        assertEquals(0, subject.getObserverCount());
    }

    @Test
    @DisplayName("subscribe 1 observer → count = 1")
    void subscribe_oneObserver_countBecomesOne() {
        subject.subscribe(obs1);
        assertEquals(1, subject.getObserverCount());
    }

    @Test
    @DisplayName("subscribe 2 observer khác nhau → count = 2")
    void subscribe_twoObservers_countBecomesTwo() {
        subject.subscribe(obs1);
        subject.subscribe(obs2);
        assertEquals(2, subject.getObserverCount());
    }

    @Test
    @DisplayName("subscribe null → bị bỏ qua, count vẫn 0")
    void subscribe_null_isIgnored() {
        subject.subscribe(null);
        assertEquals(0, subject.getObserverCount());
    }

    @Test
    @DisplayName("subscribe cùng 1 observer 2 lần → count vẫn 1 (không thêm trùng)")
    void subscribe_sameObserverTwice_notDuplicated() {
        subject.subscribe(obs1);
        subject.subscribe(obs1); // lần 2
        assertEquals(1, subject.getObserverCount());
    }

    @Test
    @DisplayName("unsubscribe → count giảm xuống")
    void unsubscribe_reducesCount() {
        subject.subscribe(obs1);
        subject.subscribe(obs2);
        subject.unsubscribe(obs1);
        assertEquals(1, subject.getObserverCount());
    }

    @Test
    @DisplayName("unsubscribe observer chưa subscribe → không crash")
    void unsubscribe_notSubscribed_noException() {
        assertDoesNotThrow(() -> subject.unsubscribe(obs1));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NOTIFY BID PLACED
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("notifyBidPlaced → cả 2 observer đều nhận được")
    void notifyBidPlaced_allObserversReceiveEvent() {
        subject.subscribe(obs1);
        subject.subscribe(obs2);

        subject.fireBidPlaced(sampleEvent);

        assertEquals(1, obs1.bidCallCount, "obs1 phải được gọi 1 lần");
        assertEquals(1, obs2.bidCallCount, "obs2 phải được gọi 1 lần");
    }

    @Test
    @DisplayName("notifyBidPlaced → observer nhận đúng giá mới (2000.0)")
    void notifyBidPlaced_observerReceivesCorrectPrice() {
        subject.subscribe(obs1);
        subject.fireBidPlaced(sampleEvent);

        assertEquals(2000.0, obs1.lastNewPrice, 0.001);
    }

    @Test
    @DisplayName("notifyBidPlaced 3 lần → observer được gọi đúng 3 lần")
    void notifyBidPlaced_calledThreeTimes_countIsThree() {
        subject.subscribe(obs1);

        subject.fireBidPlaced(sampleEvent);
        subject.fireBidPlaced(sampleEvent);
        subject.fireBidPlaced(sampleEvent);

        assertEquals(3, obs1.bidCallCount);
    }

    @Test
    @DisplayName("notifyBidPlaced sau khi unsubscribe → observer không nhận thêm")
    void notifyBidPlaced_afterUnsubscribe_notCalled() {
        subject.subscribe(obs1);
        subject.fireBidPlaced(sampleEvent);   // lần 1: nhận
        subject.unsubscribe(obs1);
        subject.fireBidPlaced(sampleEvent);   // lần 2: không nhận

        assertEquals(1, obs1.bidCallCount, "Sau unsubscribe phải vẫn là 1");
    }

    @Test
    @DisplayName("notifyBidPlaced khi không có observer → không crash")
    void notifyBidPlaced_noObservers_noException() {
        assertDoesNotThrow(() -> subject.fireBidPlaced(sampleEvent));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NOTIFY AUCTION CLOSED
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("notifyAuctionClosed → cả 2 observer đều nhận được")
    void notifyAuctionClosed_allObserversReceiveEvent() {
        subject.subscribe(obs1);
        subject.subscribe(obs2);

        subject.fireAuctionClosed(1L, "winner-001", 5000.0);

        assertEquals(1, obs1.closeCallCount);
        assertEquals(1, obs2.closeCallCount);
    }

    @Test
    @DisplayName("notifyAuctionClosed → observer nhận đúng winnerId và giá cuối")
    void notifyAuctionClosed_observerReceivesCorrectData() {
        subject.subscribe(obs1);
        subject.fireAuctionClosed(42L, "bidder-99", 7500.0);

        assertEquals("bidder-99", obs1.lastWinnerId);
        assertEquals(7500.0, obs1.lastNewPrice, 0.001);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ISOLATION: observer lỗi không ảnh hưởng observer khác
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("BrokenObserver throw exception → subject không crash, obs1 vẫn nhận được")
    void brokenObserver_doesNotBlockOtherObservers_onBidPlaced() {
        subject.subscribe(new BrokenObserver()); // đăng ký observer lỗi trước
        subject.subscribe(obs1);                 // obs1 tốt đăng ký sau

        // Không ném exception ra ngoài
        assertDoesNotThrow(() -> subject.fireBidPlaced(sampleEvent));

        // obs1 vẫn nhận được bình thường
        assertEquals(1, obs1.bidCallCount);
    }

    @Test
    @DisplayName("BrokenObserver throw exception khi close → obs1 vẫn nhận được")
    void brokenObserver_doesNotBlockOtherObservers_onAuctionClosed() {
        subject.subscribe(new BrokenObserver());
        subject.subscribe(obs1);

        assertDoesNotThrow(() -> subject.fireAuctionClosed(1L, "w", 1000.0));

        assertEquals(1, obs1.closeCallCount);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BID EVENT – kiểm tra class BidEvent trực tiếp
    // ══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("BidEvent: getAuctionId() trả đúng auctionId")
    void bidEvent_getAuctionId_correct() {
        assertEquals(1L, sampleEvent.getAuctionId());
    }

    @Test
    @DisplayName("BidEvent: getProductId() trả đúng productId")
    void bidEvent_getProductId_correct() {
        assertEquals("item-001", sampleEvent.getProductId());
    }

    @Test
    @DisplayName("BidEvent: getNewPrice() trả đúng giá mới")
    void bidEvent_getNewPrice_correct() {
        assertEquals(2000.0, sampleEvent.getNewPrice(), 0.001);
    }

    @Test
    @DisplayName("BidEvent: getBidderName() trả đúng tên người đặt")
    void bidEvent_getBidderName_correct() {
        assertEquals("Alice", sampleEvent.getBidderName());
    }

    @Test
    @DisplayName("BidEvent: getTimestamp() không null (tự gán thời điểm hiện tại)")
    void bidEvent_getTimestamp_notNull() {
        assertNotNull(sampleEvent.getTimestamp());
    }

    @Test
    @DisplayName("BidEvent: getBid() trả đúng object Bid")
    void bidEvent_getBid_notNull() {
        assertNotNull(sampleEvent.getBid());
        assertEquals("bid-001", sampleEvent.getBid().getId());
    }
}
