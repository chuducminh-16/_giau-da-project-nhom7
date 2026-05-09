import java.util.ArrayList;
import java.util.List;

public class AuctionManager {
    // 1. Tạo biến static duy nhất để chứa instance của class
    private static AuctionManager instance;
    
    // Danh sách quản lý tất cả các phiên đấu giá
    private List<Auction> auctions;

    // 2. Private Constructor: Ngăn không cho bên ngoài dùng từ khóa 'new'
    private AuctionManager() {
        auctions = new ArrayList<>();
    }

    // 3. Method static để lấy instance duy nhất
    public static AuctionManager getInstance() {
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }

    // Các hàm nghiệp vụ của Manager
    public void addAuction(Auction auction) {
        auctions.add(auction); // Thêm một phiên đấu giá mới vào danh sách quản lý
    }

    public List<Auction> getActiveAuctions() {
        return auctions; // Trả về danh sách tất cả các phiên đấu giá
    }
    
    public Auction findAuctionByItemName(String name) {
        return auctions.stream()
            .filter(a -> a.getItem().getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);       // Tìm kiếm phiên đấu giá theo tên vật phẩm, nếu không tìm thấy trả về null
    }
}