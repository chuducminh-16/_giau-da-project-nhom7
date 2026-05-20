package com.auction.shared.pattern.observer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Subject (Observable) — quản lý danh sách observer và broadcast sự kiện.
 *
 * Dùng CopyOnWriteArrayList: an toàn khi nhiều thread cùng đọc/ghi.
 * AuctionManager extend class này để có khả năng notify.
 */
public abstract class AuctionSubject {

    // Thread-safe list: subscribe/unsubscribe ít, notify nhiều → phù hợp COWAL
    private final List<AuctionObserver> observers = new CopyOnWriteArrayList<>();

    // ── Subscribe / Unsubscribe ───────────────────────────────────────────

    public void subscribe(AuctionObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unsubscribe(AuctionObserver observer) {
        observers.remove(observer);
    }

    // ── Notify helpers (gọi từ subclass) ────────────────────────────────

    /**
     * Broadcast sự kiện bid mới đến tất cả observer đang theo dõi.
     * Mỗi notify chạy trong try-catch để 1 observer lỗi không ảnh hưởng các observer khác.
     */
    protected void notifyBidPlaced(BidEvent event) {
        for (AuctionObserver obs : observers) {
            try {
                obs.onBidPlaced(event);
            } catch (Exception e) {
                System.err.println("[AuctionSubject] Observer lỗi khi nhận bid: " + e.getMessage());
            }
        }
    }

    /**
     * Broadcast sự kiện phiên đấu giá đóng.
     */
    protected void notifyAuctionClosed(long auctionId, String winnerId, double finalPrice) {
        for (AuctionObserver obs : observers) {
            try {
                obs.onAuctionClosed(auctionId, winnerId, finalPrice);
            } catch (Exception e) {
                System.err.println("[AuctionSubject] Observer lỗi khi nhận close: " + e.getMessage());
            }
        }
    }

    public int getObserverCount() { return observers.size(); }
}
