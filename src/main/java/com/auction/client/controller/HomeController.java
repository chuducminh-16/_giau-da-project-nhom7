package com.auction.client.controller;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.handler.home.HomeMessageHandler;
import com.auction.client.handler.home.HomeTableSetup;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Item;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

/**
 * THƯ MỤC CHÍNH: com.auction.client.controller
 * ĐỐI TƯỢNG: HomeController
 * CHỨC NĂNG: Quản lý toàn bộ các tương tác trên màn hình Trang chủ (Home View).
 * Bao gồm: Cập nhật thông tin tấm Banner nổi bật (Hero Card) ở trên, điều khiển danh sách
 * sản phẩm đấu giá ở TableView phía dưới, lọc tìm kiếm sản phẩm và chuyển đổi màn hình chức năng.
 */
public class HomeController implements Initializable {

    // =========================================================================
    // 1. CÁC THÀNH PHẦN GIAO DIỆN KHAI BÁO TỪ FILE FXML (KẾT NỐI VỚI home-view.fxml)
    // =========================================================================
    @FXML private TextField searchField;       // Thanh tìm kiếm sản phẩm theo từ khóa tên
    @FXML private Label     userLabel;         // Nhãn hiển thị thông tin Username người dùng ở góc trái dưới/thanh trạng thái

    // --- Các Node giao diện thuộc vùng Banner Đen hiển thị sản phẩm nổi bật (Hero Card) ---
    @FXML private Label     heroName;          // Chữ hiển thị tên sản phẩm nổi bật
    @FXML private Label     heroBid;           // Chữ hiển thị giá tiền hiện tại (đã định dạng VND)
    @FXML private Label     heroStatus;        // Chữ hiển thị trạng thái đấu giá (Vd: Lot • RUNNING)
    @FXML private Label     heroDesc;          // Chữ hiển thị phần mô tả chi tiết sản phẩm
    @FXML private ImageView heroImage;         // Khung chứa hình ảnh minh họa cho sản phẩm
    @FXML private Label     heroPlaceholder;   // Nhãn hiển thị icon/chữ mặc định khi sản phẩm chưa có ảnh
    @FXML private Label     heroTime;          // Đồng hồ đếm ngược thời gian thực (Realtime) riêng cho Hero Card

    // --- Các Node giao diện cấu thành nên Bảng danh sách phiên đấu giá đang diễn ra ---
    @FXML private TableView<Item>           auctionTable;       // Thành phần cấu trúc bảng chứa danh sách dữ liệu
    @FXML private TableColumn<Item, String> colAuctionName;     // Cột hiển thị: Tên sản phẩm
    @FXML private TableColumn<Item, Double> colAuctionPrice;    // Cột hiển thị: Giá đấu hiện tại
    @FXML private TableColumn<Item, LocalDateTime> colAuctionEndTime; // Cột chứa dữ liệu gốc ngày kết thúc (dùng để tính toán đếm ngược)
    @FXML private TableColumn<Item, String> colAuctionTime;     // Cột hiển thị: Chuỗi ký tự đếm ngược "Còn lại" (Ngày giờ phút giây)
    @FXML private TableColumn<Item, String> colAuctionStatus;   // Cột hiển thị: Trạng thái text (RUNNING, FINISHED)
    @FXML private TableColumn<Item, Void>   colAuctionAction;   // Cột chức năng: Chứa nút bấm "Tham gia" chuyển vùng

    // =========================================================================
    // 2. CÁC BIẾN QUẢN LÝ DỮ LIỆU NỘI BỘ VÀ ĐỒNG HỒ ENGINE (TIMELINE)
    // =========================================================================
    // Danh sách động (ObservableList) đóng vai trò làm bộ đệm trung gian giữ dữ liệu liên kết trực tiếp với TableView
    private final ObservableList<Item> auctionList = FXCollections.observableArrayList(); 
    
    private Item heroItem;             // Đối tượng Item hiện tại đang được chọn để bung thông tin ra Banner đen
    private Timeline heroTimeline;     // Bộ đếm thời gian chạy lặp lại mỗi 1 giây để cập nhật chữ cho đồng hồ trên Banner
    
    /**
     * TÍNH NĂNG SỬA LỖI 1: Luồng Timeline đồng hồ toàn cục cho TableView.
     * Cứ sau mỗi chu kỳ 1 giây, bộ đếm này sẽ bắt ép toàn bộ cấu trúc TableView làm mới (refresh) giao diện.
     * Việc làm mới này kích hoạt các CellFactory (được định nghĩa trong HomeTableSetup) chạy lại logic tính toán thời gian,
     * giúp đồng hồ đếm ngược của TẤT CẢ các dòng trong bảng nhảy giây đồng loạt cùng lúc, sửa dứt điểm lỗi đứng im thời gian.
     */
    private Timeline globalTableTimeline; 

    // =========================================================================
    // 3. THÀNH PHẦN KẾT NỐI MẠNG (SOCKET CLIENT / LISTENERS)
    // =========================================================================
    private final NetworkClient client = NetworkClient.getInstance(); // Khởi tạo / Lấy đối tượng kết nối Socket duy nhất (Singleton)
    private HomeMessageHandler messageHandler;                       // Lớp chịu trách nhiệm bóc tách và phân phối tin nhắn từ Server đổ về
    private NetworkClient.MessageListener listener;                  // Biến đại diện cho hàm callback lắng nghe dòng sự kiện luồng mạng

    // =========================================================================
    // 4. HÀM KHỞI TẠO CHÍNH (INITIALIZE) - CHẠY NGAY KHI MÀN HÌNH ĐƯỢC LOAD
    // =========================================================================
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 4.1. Truy vấn tên tài khoản từ phiên làm việc (Session) để hiển thị lời chào người dùng lên UI
        String username = UserSession.getInstance().getUsername();
        if (userLabel != null && username != null) {
            userLabel.setText("👤  " + username);
        }

        // 4.2. Khởi tạo bộ gom tin nhắn mạng và gán hàm listener để nhận dữ liệu bất đồng bộ từ Server truyền xuống
        this.messageHandler = new HomeMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerMessage(msg);
        client.addListener(listener);

        // 4.3. Ràng buộc danh sách động auctionList vào TableView. Khi danh sách này thay đổi, bảng tự động thay đổi theo.
        auctionTable.setItems(auctionList);

        // 4.4. Gọi lớp cấu hình độc lập HomeTableSetup để định dạng kiểu hiển thị, màu sắc chữ cho từng cột trong bảng
        HomeTableSetup.setupTable(
            this, 
            auctionTable, 
            colAuctionName, 
            colAuctionPrice, 
            colAuctionEndTime, 
            colAuctionTime, 
            colAuctionStatus, 
            colAuctionAction
        );

        // 4.5. [KÍCH HOẠT FIX LỖI 1]: Khởi chạy luồng đếm ngược chạy ngầm ép bảng cập nhật thời gian thực mỗi giây
        startGlobalTableTimeline();

        // 4.6. [KÍCH HOẠT FIX LỖI 2]: Đăng ký bộ lắng nghe sự kiện chọn hàng (Selection Listener) cho bảng dữ liệu.
        // Bất cứ khi nào người dùng nhấp chuột chọn một dòng sản phẩm khác nhau trên bảng (Vd: Gundam, Macbook Pro),
        // hệ thống lập tức bắt được đối tượng (newSelection) và truyền vào hàm updateHeroCard() để thay đổi thông tin trên Banner đen.
        auctionTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                updateHeroCard(newSelection); // Cập nhật nội dung Banner đen tương ứng với hàng vừa click chuột
            }
        });

        // 4.7. Phát đi một chuỗi tin nhắn Socket định dạng JSON yêu cầu Server kết nối cung cấp danh sách hàng đấu giá mới nhất
        client.send(new Message("GET_AUCTIONS", "{}"));
    }

    /**
     * PHƯƠNG THỨC HỖ TRỢ FIX LỖI 1: Khởi tạo và kích hoạt vòng lặp thời gian vô hạn cho TableView.
     * Đảm bảo giao diện bảng luôn được làm mới theo từng giây đồng hồ máy tính.
     */
    private void startGlobalTableTimeline() {
        if (globalTableTimeline != null) {
            globalTableTimeline.stop(); // Hủy bộ đếm cũ nếu có để tránh tình trạng tạo trùng lặp luồng chạy chồng chéo
        }
        
        // Định nghĩa một chu kỳ KeyFrame thực hiện nhiệm vụ sau mỗi khoảng thời gian đúng 1 giây
        globalTableTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (auctionTable != null) {
                // Lệnh cốt lõi: Ép TableView vẽ lại toàn bộ giao diện của mình, kích hoạt các hàm tính toán giây nhảy liên tục
                auctionTable.refresh(); 
            }
        }));
        globalTableTimeline.setCycleCount(Animation.INDEFINITE); // Thiết lập chế độ lặp lại vô hạn không dừng
        globalTableTimeline.play(); // Bắt đầu kích hoạt luồng chạy
    }

    // =========================================================================
    // 5. LOGIC DỰ LIỆU: ĐẨY THÔNG TIN SẢN PHẨM LÊN BANNER ĐEN (HERO CARD)
    // =========================================================================
    public void updateHeroCard(Item item) {
    if (item == null) return;
    this.heroItem = item;

    if (heroName   != null) heroName.setText(item.getName());
    if (heroBid    != null) heroBid.setText(String.format("%,.0f VND", item.getCurrentBid()));
    if (heroStatus != null) heroStatus.setText("Lot • " + item.getStatus());
    if (heroDesc   != null) {
        String desc = item.getDescription();
        heroDesc.setText(desc != null && !desc.isBlank() ? desc : (item.getSellerName() != null ? item.getSellerName() : ""));
    }

    if (heroImage != null) {
        String rawPath = item.getImagePath();
        String path = (rawPath != null && rawPath.contains("|")) ? rawPath.split("\\|")[0] : rawPath;

        if (path != null && !path.isBlank()) {
            try {
                String imageUrl;
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    imageUrl = path;
                } else {
                    java.io.File imgFile = new java.io.File(path);
                    if (!imgFile.exists()) {
                        heroImage.setImage(null);
                        if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
                        return;
                    }
                    imageUrl = imgFile.toURI().toString();
                }
                javafx.scene.image.Image img = new javafx.scene.image.Image(imageUrl, true);
                heroImage.setImage(img);
                if (heroPlaceholder != null) heroPlaceholder.setVisible(false);
            } catch (Exception e) {
                heroImage.setImage(null);
                if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
            }
        } else {
            heroImage.setImage(null);
            if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
        }
    }

    if (heroTimeline != null) {
        heroTimeline.stop();
    }

    if (heroTime != null) {
        if (item.getEndTime() == null) {
            heroTime.setText("");
            return;
        }

        heroTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            java.time.LocalDateTime endTime = item.getEndTime();
            java.time.LocalDateTime now = java.time.LocalDateTime.now();

            if (now.isAfter(endTime)) {
                heroTime.setText("⏳ ĐÃ KẾT THÚC");
                heroTime.setStyle("-fx-text-fill: #e53e3e; -fx-font-size: 13; -fx-font-weight: bold;");
                heroTimeline.stop();
            } else {
                long days    = java.time.temporal.ChronoUnit.DAYS.between(now, endTime);
                long hours   = java.time.temporal.ChronoUnit.HOURS.between(now, endTime) % 24;
                long minutes = java.time.temporal.ChronoUnit.MINUTES.between(now, endTime) % 60;
                long seconds = java.time.temporal.ChronoUnit.SECONDS.between(now, endTime) % 60;

                heroTime.setStyle("-fx-text-fill: #fc8181; -fx-font-size: 13; -fx-font-weight: bold;");
                if (days > 0) {
                    heroTime.setText(String.format("⏳ Còn %d ngày %02d:%02d:%02d", days, hours, minutes, seconds));
                } else {
                    heroTime.setText(String.format("⏳ Còn %02d:%02d:%02d", hours, minutes, seconds));
                }
            }
        }));
        heroTimeline.setCycleCount(Animation.INDEFINITE);
        heroTimeline.play();
    }
}

    // =========================================================================
    // 6. CÁC PHƯƠNG THỨC ĐIỀU HƯỚNG VÀ XỬ LÝ SỰ KIỆN CLICK CHUỘT (EVENT HANDLERS)
    // =========================================================================
    
    /**
     * Hàm trung gian điều hướng chuyển màn hình đi tới trang Chi tiết của một sản phẩm bất kỳ.
     */
    public void openDetail(Item item, ActionEvent event) {
        cleanUpBeforeLeave(); // Dọn dẹp sạch sẽ các luồng chạy ngầm của màn hình hiện tại trước khi rời đi
        SelectedProductSession.getInstance().setProductId(item.getId()); // Ghi ID sản phẩm định xem vào Session tĩnh để DetailController lấy dùng
        SceneEngine.changeScene(event, "detail-view.fxml", "The Curator - Chi tiết sản phẩm"); // Gọi Engine thực hiện đổi giao diện
    }

    // Sự kiện khi người dùng click chuột vào nút màu xanh "Place Bid ->" nằm trên Banner đen
    @FXML public void onHeroBidClicked(ActionEvent event) {
        if (heroItem != null) openDetail(heroItem, event); // Chuyển màn hình xem chi tiết sản phẩm đang ghim trên Banner
    }

    // Sự kiện khi người dùng click vào nút "Làm mới" nằm ở góc trên bên phải của vùng bảng danh sách
    @FXML public void onRefreshAuctions(ActionEvent event) {
        client.send(new Message("GET_AUCTIONS", "{}")); // Phát lệnh yêu cầu Server cập nhật và gửi lại danh sách phòng mới nhất
    }

    // Sự kiện tìm kiếm: Chạy lập tức khi người dùng nhập text vào ô Search và gõ phím ENTER
    @FXML public void onSearchEnter(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER) return; // Nếu phím nhấn xuống không phải ENTER thì bỏ qua không xử lý
        String keyword = searchField.getText().trim().toLowerCase(); // Chuẩn hóa chuỗi tìm kiếm về dạng chữ thường và cắt khoảng trắng thừa
        
        if (keyword.isEmpty()) {
            auctionTable.setItems(auctionList); // Nếu ô tìm kiếm rỗng, gán lại danh sách gốc ban đầu để hiển thị đầy đủ
        } else {
            // Sử dụng tính năng Predicate filtered để lọc động danh sách: Chỉ giữ lại sản phẩm có tên chứa từ khóa tìm kiếm
            auctionTable.setItems(auctionList.filtered(p -> p.getName().toLowerCase().contains(keyword)));
        }
    }

    // Sự kiện khi nhấn mục điều hướng "Danh sách đấu giá" ở thanh Menu Sidebar bên trái
    @FXML public void onSideAuctionsClick(ActionEvent event) {
        auctionTable.setItems(auctionList); // Đặt lại bảng hiển thị toàn bộ dữ liệu danh sách ban đầu
    }

    // Sự kiện khi nhấn mục điều hướng "Lịch sử đấu giá" ở thanh Menu Sidebar bên trái
    @FXML public void onBidHistoryClick(ActionEvent event) {
        cleanUpBeforeLeave();
        SceneEngine.changeScene(event, "bid-history-view.fxml", "The Curator - Lịch sử đấu giá");
    }

    // Sự kiện khi nhấn mục điều hướng "Đăng sản phẩm" / Dashboard người bán ở thanh Menu bên trái
    @FXML public void onSellerDashboardClick(ActionEvent event) {
        cleanUpBeforeLeave();
        SceneEngine.changeScene(event, "manage-product-view.fxml", "The Curator - Seller Dashboard");
    }

    /**
     * TÍNH NĂNG SỬA LỖI 3: Sự kiện click chuột vào nút bấm ACC (Account) ở góc phải trên cùng màn hình.
     * Chịu trách nhiệm ngắt kết nối tạm thời luồng chạy ngầm của trang chủ và dẫn người dùng tới trang Hồ sơ cá nhân.
     * * 💡 [GIẢI THÍCH KẾT NỐI NGỮ CẢNH 2-IN-1]:
     * - Tại đây, hàm gọi trực tiếp file giao diện gốc "profile-view.fxml".
     * - Do UserSession đã tồn tại dữ liệu đăng nhập, ProfileController khi được kích hoạt sẽ tự động nhận diện,
     * bật cờ `isProfileMode = true`, tự động đổ dữ liệu và đổi giao diện sang chế độ xem/sửa thông tin cá nhân.
     */
    @FXML 
    public void onAccountClick(ActionEvent event) {
        cleanUpBeforeLeave(); // Dọn dẹp luồng đồng hồ và listener để tránh hiện tượng rò rỉ bộ nhớ (Memory Leak)
        
        // Gọi SceneEngine để chuyển đổi giao diện sang màn hình Profile đa năng
        SceneEngine.changeScene(event, "profile-view.fxml", "The Curator - Hồ sơ cá nhân");
    }

    // Sự kiện khi người dùng nhấn chuột vào nút màu đỏ "Đăng xuất" ở góc phải trên cùng
    @FXML public void onLogoutClick(ActionEvent event) {
        cleanUpBeforeLeave();
        UserSession.getInstance().logout();           // Xóa sạch dữ liệu định danh tài khoản lưu trong Session hệ thống
        SelectedProductSession.getInstance().clear(); // Giải phóng ID sản phẩm đang chọn dở
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }
    @FXML public void onWalletClick(ActionEvent event) {
    try {
        javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
            getClass().getResource("/com/auction/client/view/fxml/wallet-view.fxml"));
        javafx.scene.Parent root = loader.load();
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("💰 Nạp tiền");
        stage.setScene(new javafx.scene.Scene(root));
        stage.setResizable(false);
        stage.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        stage.show();
    } catch (Exception e) { e.printStackTrace(); }
}

    /**
     * PHƯƠNG THỨC KHỬ TRÀN BỘ NHỚ (ANTI-MEMORY LEAK):
     * Cực kỳ quan trọng trong lập trình JavaFX Socket. Phải luôn luôn được gọi trước khi chuyển màn hình
     * nhằm gỡ bộ lắng nghe mạng cũ và ép dừng tất cả các Timeline (Interval) đang chạy lặp lại vô hạn,
     * ngăn chặn hiện tượng luồng chạy ngầm ăn mòn tài nguyên RAM máy tính dẫn đến crash ứng dụng về sau.
     */
    private void cleanUpBeforeLeave() {
        client.removeListener(listener); // Gỡ bỏ listener tiếp nhận tin nhắn của HomeController ra khỏi danh sách hệ thống mạng mạng
        if (heroTimeline != null) {
            heroTimeline.stop(); // Hủy và dừng hẳn đồng hồ đếm ngược của vùng Banner đen
        }
        if (globalTableTimeline != null) {
            globalTableTimeline.stop(); // Hủy và dừng hẳn đồng hồ tự động refresh nhảy giây của bảng danh sách phía dưới
        }
    }

    @FXML public void onCardBidClicked(ActionEvent event) {}

    // =========================================================================
    // 7. CÁC PHƯƠNG THỨC TRUY XUẤT DỮ LIỆU TIÊU CHUẨN (GETTERS)
    // =========================================================================
    public ObservableList<Item> getAuctionList() { return auctionList; }
    public Item getHeroItem() { return heroItem; }
    public TableView<Item> getAuctionTable() { return auctionTable; }
}