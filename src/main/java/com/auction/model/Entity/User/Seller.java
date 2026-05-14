package com.auction.model.Entity.User;
import java.util.ArrayList;
import java.util.List;
public class Seller extends User {
    private double rating;
    private List<String> itemIds; // Danh sách ID các món hàng đang bán
    public Seller(String id, String username, String email, String password, double rating) {
        super(id, username, email, password);
        this.rating = 5.0; // Mặc định đánh giá ban đầu là 5 sao
        this.itemIds = new ArrayList<>();
    }
    public double getRating() {  // Xem đánh giá
        return rating;
    }

    public void setRating(double rating) { // Cập nhật đánh giá
        this.rating = rating;
    }

    public void addItem(String itemId) { // Thêm món hàng mới vào danh sách bán
        this.itemIds.add(itemId);
    }

    @Override
    public void displayRole(){
        System.out.println("[Seller] Username: " + getUsername() + " | Rating: " + rating + "⭐");
    }
    @Override
    public String getRole() { return "SELLER"; }
}

