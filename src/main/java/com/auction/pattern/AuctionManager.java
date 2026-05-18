package com.auction.pattern;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import com.auction.model.Entity.Auction_Bid.Auction;

/**
 * Singleton quản lý toàn bộ phiên đấu giá.
 *
 * Bản gốc không thread-safe (double-checked locking thiếu volatile).
 * Bản này dùng Initialization-on-demand holder – lazy, thread-safe, không cần synchronized.
 */
public class AuctionManager {

    // CopyOnWriteArrayList: đọc không cần lock, phù hợp nhiều thread đọc ít ghi
    private final List<Auction> auctions = new CopyOnWriteArrayList<>();

    // ---- Singleton: Initialization-on-demand holder ----
    private AuctionManager() {}

    private static final class Holder {
        static final AuctionManager INSTANCE = new AuctionManager();
    }

    public static AuctionManager getInstance() {
        return Holder.INSTANCE;
    }

    // ------------------------------------------------------------------ //
    //  Nghiệp vụ                                                          //
    // ------------------------------------------------------------------ //

    public void addAuction(Auction auction) {
        auctions.add(auction);
    }

    public void removeAuction(long auctionId) {
        auctions.removeIf(a -> a.getId() == auctionId);
    }

    /** Trả về danh sách bất biến để tránh thay đổi ngoài ý muốn. */
    public List<Auction> getAllAuctions() {
        return Collections.unmodifiableList(auctions);
    }

    /** Chỉ lấy phiên đang active. */
    public List<Auction> getActiveAuctions() {
        return auctions.stream()
                .filter(Auction::isActive)
                .toList();
    }

    public Optional<Auction> findById(long id) {
        return auctions.stream()
                .filter(a -> a.getId() == id)
                .findFirst();
    }

    public Optional<Auction> findByItemName(String name) {
        return auctions.stream()
                .filter(a -> a.getItem().getName().equalsIgnoreCase(name))
                .findFirst();
    }
}