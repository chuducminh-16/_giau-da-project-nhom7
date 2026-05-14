package com.auction.model.Entity.Auction_Bid;
import java.util.ArrayList;
import java.util.List;

import com.auction.model.Entity.Item.Item;
import com.auction.model.Entity.User.Bidder;
import com.auction.model.Entity.User.Seller;

public class Auction {
    private Item item;  //vật phẩm
    private Seller seller;  //người bán
    private List<Bid> bidHistory; //danh sách các giá 
    private double currentPrice;//giá hiện tại
    private boolean isActive; //trạng thái 
    public Auction(Item item, Seller seller, double startingPrice) {
        this.item = item;
        this.seller = seller;
        this.currentPrice = startingPrice;
        this.bidHistory = new ArrayList<>();
        this.isActive = true;
    }

    // Logic quan trọng nhất: Đặt giá mới
    public boolean placeBid(Bidder bidder, double amount) {
        if (!isActive) {
            System.out.println("Phiên đấu giá đã kết thúc!");
            return false;
        }

        if (amount > currentPrice) {
            Bid newBid = new Bid(bidder, amount);
            bidHistory.add(newBid);
            currentPrice = amount;
            System.out.println("Đặt giá thành công: " + amount + " bởi " + bidder.getUsername());
            return true;
        } else {
            System.out.println("Giá đặt phải cao hơn giá hiện tại: " + currentPrice);
            return false;
        }
    }

    public Bid getHighestBid() {
        if (bidHistory.isEmpty()) return null; // Nếu chưa có giá nào được đặt trả về null
        return bidHistory.get(bidHistory.size() - 1); // Giá cao nhất luôn là giá cuối cùng trong danh sách
    }
    public void endAuction() {
        this.isActive = false;   // Kết thúc phiên đấu giá
    }
    public Item getItem() { return item; }    // Lấy thông tin vật phẩm
    public double getCurrentPrice() { return currentPrice; }  // Lấy giá hiện tại
}
