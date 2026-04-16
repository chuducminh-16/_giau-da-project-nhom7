package com.auction.model;

import java.util.ArrayList;
import java.util.List;

public class AuctionManager {
    // Biến static duy nhất lưu trữ instance của class
    private static AuctionManager instance;
    private List<Item> itemList;

    // Constructor private để không cho phép tạo đối tượng từ bên ngoài
    private AuctionManager() {
        itemList = new ArrayList<>();
    }

    // Phương thức public static để các lớp khác truy cập vào instance duy nhất
    public static synchronized AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    public void addItem(Item item) { itemList.add(item); }
    public List<Item> getAllItems() { return itemList; }
}