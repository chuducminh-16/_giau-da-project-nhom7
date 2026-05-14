package Tuần 7.Interface_Observer;

// Observer cho UI
public class AuctionGUIObserver implements AuctionObserver {
    @Override
    public void onNewBid(double newPrice, String bidderName) {
        // Cập nhật lên màn hình JavaFX
        System.out.println("GUI update: New price is " + newPrice);
    }
}

// Observer cho Log hệ thống
public class AuctionLogObserver implements AuctionObserver {
    @Override
    public void onNewBid(double newPrice, String bidderName) {
        // Ghi vào file log
        System.out.println("Log update: Bidder " + bidderName + " bid " + newPrice);
    }
}
