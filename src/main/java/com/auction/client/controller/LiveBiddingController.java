package com.auction.client.controller;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
import com.auction.client.handler.livebidding.AutoBidHandler;
import com.auction.client.handler.livebidding.CountdownHandler;
import com.auction.client.handler.livebidding.LiveBiddingChartManager;
import com.auction.client.handler.livebidding.LiveBiddingMessageHandler;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonDeserializer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.UnaryOperator;

/**
 * 👑 LỚP ĐIỀU KHIỂN TRUNG TÂM (LIVE BIDDING UI CONTROLLER)
 * - Đóng vai trò là cổng kết nối trung gian tiếp nhận các thành phần giao diện đồ họa FXML.
 * - Phân phối công việc thông qua kiến trúc Delegation pattern cho các lớp Handler chuyên biệt.
 */
public class LiveBiddingController implements Initializable {

    // ── 📌 KHAI BÁO CÁC PHẦN TỬ PHẢN XẠ FXML UI BINDING ──────────────────────
    @FXML private Label lblCountdown;         // Nhãn hiển thị đồng hồ đếm ngược thời gian chốt
    @FXML private Label lblProductName;       // Nhãn tên vật phẩm đấu giá
    @FXML private Label lblCurrentBid;        // Nhãn thông báo mức giá hiện tại tốt nhất
    @FXML private TextField txtBidAmount;      // Ô nhập số tiền muốn đặt giá thủ công
    @FXML private LineChart<String, Number> priceChart; // Khung lưới đồ thị được định nghĩa sẵn từ FXML

    @FXML private TableView<BidItem> tableBidHistory;             // Bảng lịch sử các lượt đặt giá công khai
    @FXML private TableColumn<BidItem, String> colTime;           // Cột hiển thị mốc thời gian
    @FXML private TableColumn<BidItem, String> colUser;           // Cột hiển thị tên tài khoản đấu giá
    @FXML private TableColumn<BidItem, String> colAmount;         // Cột hiển thị số tiền đặt cược
    @FXML private ListView<String> listNotifications;             // Khung danh sách nhật ký thông báo hệ thống

    @FXML private TextField txtMaxBid;         // Ô cấu hình mức trần tối đa cho Auto-bid
    @FXML private Button btnAutoBid;           // Nút kích hoạt/Hủy kích hoạt Auto-bid
    @FXML private Label lblAutoBidStatus;      // Nhãn trạng thái bật/tắt chữ (Đang chạy... / Đang tắt)
    @FXML private Label lblAutoBidInfo;        // Nhãn chi tiết mô tả bước nhảy của Auto-bid
    @FXML private HBox hboxAutoBidInfo;        // Thanh layout bọc vùng thông báo Auto-bid

    // ── 🤝 THÀNH PHẦN ỦY QUYỀN (DELEGATE HANDLERS) ──────────────────────────
    private AutoBidHandler autoBidHandler;     // Xử lý logic nghiệp vụ đặt giá tự động dựa trên bước nhảy
    private CountdownHandler countdownHandler; // Điều hành bộ đếm ngược luồng Task riêng định kỳ mỗi giây
    private LiveBiddingChartManager chartManager;   // 🆕 Quản trị và ép mã màu đỏ đặc/rỗng cho LineChart
    private LiveBiddingMessageHandler messageHandler; // 🆕 Lắng nghe, parse chuỗi JSON nhận từ Server TCP/IP

    // ── 📦 BIẾN TRẠNG THÁI NỘI BỘ PHÒNG ĐẤU GIÁ ──────────────────────────────
    private Item currentItem;                  // Đối tượng Model thông tin sản phẩm hiện hành
    private String itemId;                     // ID định danh duy nhất của sản phẩm
    private long secondsRemaining = -1;        // Thời lượng đếm ngược còn lại tính bằng giây
    private double latestBid = 0;              // Mức giá cao nhất ghi nhận được ở thời điểm hiện tại
    private boolean historyLoaded = false;     // Cờ hiệu đánh dấu hệ thống đã tải xong lịch sử ban đầu chưa

    // Các mảng ObservableList liên kết đồng bộ trực tiếp với các bảng hiển thị trên giao diện người dùng (UI)
    private final ObservableList<BidItem> bidHistory = FXCollections.observableArrayList();
    private final ObservableList<String> notifications = FXCollections.observableArrayList();

    // Đối tượng phân tích cú pháp chuỗi JSON, hỗ trợ cơ chế Polymorphism đa hình mẫu vật phẩm
    private final Gson gson = buildGson();
    private final NetworkClient client = NetworkClient.getInstance(); // Kết nối Socket đơn mảnh (Singleton)
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss"); 
    
    // Đăng ký bộ thu lắng nghe tin nhắn: Chuyển giao toàn bộ payload sang lớp messageHandler đảm nhiệm
    private final NetworkClient.MessageListener listener = msg -> messageHandler.processMessage(msg);

    /**
     * Thiết lập cơ chế bóc tách Gson thông minh tự phát hiện loại thực thể Con 
     * (Art, Electronics, Vehicle) kế thừa từ Class cha Item dựa trên thuộc tính "type".
     */
    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Item.class, (JsonDeserializer<Item>) (json, typeOfT, ctx) -> {
                    JsonObject obj = json.getAsJsonObject();
                    String type = obj.has("type") && !obj.get("type").isJsonNull()
                            ? obj.get("type").getAsString().toUpperCase() : "ART";
                    return switch (type) {
                        case "ELECTRONICS" -> ctx.deserialize(obj, Electronics.class);
                        case "VEHICLE"     -> ctx.deserialize(obj, Vehicle.class);
                        default            -> ctx.deserialize(obj, Art.class);
                    };
                })
                .create();
    }

    /**
     * Hàm kích hoạt tự động của JavaFX khi màn hình giao diện FXML bắt đầu được nạp lên bộ nhớ
     */
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Khởi tạo và liên kết các lớp quản lý phụ trách chuyên môn nhỏ
        this.autoBidHandler = new AutoBidHandler(this);
        this.countdownHandler = new CountdownHandler(this);
        this.chartManager = new LiveBiddingChartManager(priceChart); // Giao quyền điều khiển biểu đồ gốc FXML
        this.messageHandler = new LiveBiddingMessageHandler(this);   // Giao quyền nhận gói tin Server

        setupTable();                  // Ánh xạ liên kết dữ liệu mảng vào TableView UI
        client.addListener(listener); // Đăng ký lắng nghe kênh dữ liệu Network Socket

        // ⚡ KÍCH HOẠT ĐỊNH DẠNG PHÂN TÁCH HÀNG NGHÌN THỜI GIAN THỰC CHO HAI Ô NHẬP TIỀN
        applyMoneyFormatter(txtBidAmount);
        applyMoneyFormatter(txtMaxBid);

        // Lấy ID sản phẩm đã chọn từ màn hình danh sách trước đó lưu trong Session toàn cục
        itemId = SelectedProductSession.getInstance().getProductId();

        if (itemId != null) {
            addLog("Đang kết nối đến phòng đấu giá trực tuyến...");
            // Gửi bộ 3 lệnh đăng ký theo dõi phòng lên hệ thống Server
            client.send(new Message("GET_PRODUCT_DETAIL", gson.toJson(Map.of("itemId", itemId))));
            client.send(new Message("GET_BID_HISTORY", gson.toJson(Map.of("productId", itemId))));
            client.send(new Message("WATCH_AUCTION", gson.toJson(Map.of("auctionId", itemId))));
        } else {
            addLog("Lỗi hệ thống: Không xác định được sản phẩm cần đấu giá!");
        }

        autoBidHandler.restoreSession(itemId, latestBid); // Phục hồi trạng thái Auto-bid cũ nếu người dùng vô tình bấm F5 thoát ra vào lại

        // Cài đặt hộp thoại Tooltip gợi ý cách dùng nút chức năng Đặt giá tự động
        if (btnAutoBid != null) {
            Tooltip tip = new Tooltip("Nhập giá giới hạn rồi bấm kích hoạt.\nHệ thống sẽ tự động tăng giá hơn đối thủ 1 bước nhảy.");
            tip.setStyle("-fx-background-color:#1a1f2e; -fx-text-fill:white; -fx-font-size:12px; -fx-padding:8 12;");
            Tooltip.install(btnAutoBid, tip);
        }

        // ⌨️ TIỆN ÍCH NGƯỜI DÙNG: Bắt sự kiện bấm phím Enter tại ô nhập tiền để tự kích hoạt đặt giá không cần di chuột bấm nút
        Platform.runLater(() -> {
            if (txtBidAmount != null && txtBidAmount.getScene() != null) {
                txtBidAmount.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.getCode() == KeyCode.ENTER && txtBidAmount.isFocused()) {
                        onPlaceBidClick(new ActionEvent(txtBidAmount, null)); // Giả lập hành động click chuột
                        event.consume(); // Chặn lan truyền sự kiện tránh lỗi trùng phím
                    }
                });
            }
        });
    }

    /**
     * 🛠️ HÀM BỔ TRỢ: Tạo bộ lọc TextFormatter tự động thêm dấu phân cách chấm (.) khi người dùng đang gõ
     */
    private void applyMoneyFormatter(TextField textField) {
        if (textField == null) return;

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.'); // Sử dụng dấu chấm làm dấu phân tách hàng nghìn theo văn hóa VN
        DecimalFormat df = new DecimalFormat("#,###", symbols);

        UnaryOperator<TextFormatter.Change> filter = change -> {
            if (change.isContentChange()) {
                String newText = change.getControlNewText();
                // Chỉ cho phép nhập các ký tự số, loại bỏ mọi chữ cái hoặc ký tự đặc biệt
                String digits = newText.replaceAll("[^\\d]", "");
                
                if (digits.isEmpty()) {
                    change.setText("");
                    change.setRange(0, change.getControlText().length());
                    return change;
                }

                try {
                    double value = Double.parseDouble(digits);
                    String formatted = df.format(value);

                    // Thiết lập lại nội dung hiển thị mới đã được thêm dấu chấm phân tách
                    change.setText(formatted);
                    change.setRange(0, change.getControlText().length());
                    
                    // Tính toán lại vị trí con trỏ chuột một cách thông minh để không bị nhảy lùi tự động
                    int caretOffset = formatted.length() - newText.length();
                    int newCaretPos = change.getCaretPosition() + caretOffset;
                    if (newCaretPos >= 0 && newCaretPos <= formatted.length()) {
                        change.setCaretPosition(newCaretPos);
                        change.setAnchor(newCaretPos);
                    }
                } catch (NumberFormatException e) {
                    return null; // Từ chối thay đổi nếu bị tràn số
                }
            }
            return change;
        };

        textField.setTextFormatter(new TextFormatter<>(filter));
    }

    /**
     * Làm mới nội dung ô nhập tiền, tự tính toán mức giá tối thiểu hợp lệ để hiển thị gợi ý (Prompt Text)
     */
    public void resetAndTargetNextBidPrompt() {
        if (txtBidAmount == null) return;
        txtBidAmount.clear(); 

        if (currentItem != null) {
            // Giá tối thiểu tiếp theo = Mức giá trần hiện tại + Bước nhảy quy định của sản phẩm
            double effectiveBid = Math.max(latestBid, currentItem.getCurrentBid());
            double nextMinBid = effectiveBid + currentItem.getBidIncrement();
            txtBidAmount.setPromptText(String.format("Mức giá tối thiểu: %,.0f VNĐ", nextMinBid));
        } else {
            txtBidAmount.setPromptText("Nhập số tiền muốn đặt...");
        }
    }

    /**
     * Bắt sự kiện khi người dùng nhấn chuột vào nút chuyển đổi bật/tắt Auto-Bid
     */
    @FXML
    public void onAutoBidToggle(ActionEvent event) {
        autoBidHandler.toggleAutoBid();
    }

    /**
     * Bắt sự kiện khi người dùng click chuột trực tiếp vào nút "ĐẶT GIÁ" thủ công
     */
    @FXML
    public void onPlaceBidClick(ActionEvent event) {
        if (currentItem == null) {
            addLog("Đang tải dữ liệu sản phẩm từ hệ thống, vui lòng đợi trong giây lát...");
            return;
        }
        if (secondsRemaining == 0) {
            addLog("Lỗi: Phiên đấu giá đã kết thúc, không thể đặt thêm giá!");
            return;
        }

        String input = txtBidAmount.getText().trim();
        if (input.isEmpty()) return;

        double newAmount;
        try {
            // 🔄 GIẢI PHÁP: Loại bỏ sạch sẽ dấu chấm định dạng hiển thị trước khi gửi lên Server
            newAmount = Double.parseDouble(input.replaceAll("\\.", "")); 
        } catch (NumberFormatException e) {
            addLog("Lỗi nhập liệu: Số tiền đặt giá không hợp lệ!");
            return;
        }

        // Thẩm định luật chơi dưới Client trước khi đẩy lệnh lên Server để tăng trải nghiệm mượt mà
        double effectiveBid = Math.max(latestBid, currentItem.getCurrentBid());
        double minBid = effectiveBid + currentItem.getBidIncrement();

        if (newAmount < minBid) {
            addLog(String.format("❌ Giá trả không hợp lệ! Mức giá tiếp theo phải tối thiểu là %,.0f VNĐ", minBid));
            return;
        }

        // Đóng gói tin nhắn và bắn trực tiếp lên luồng Socket Server để tranh chấp giá trần
        client.send(new Message("PLACE_BID", gson.toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId", UserSession.getInstance().getUserId(),
                "amount", newAmount
        ))));

        addLog(String.format("Đã gửi đề xuất đặt giá %,.0f VNĐ lên máy chủ...", newAmount));
        resetAndTargetNextBidPrompt(); 
    }

    /**
     * Bắt sự kiện khi người dùng muốn rời khỏi phòng đấu giá, quay về trang thông tin chi tiết
     */
    @FXML
    public void onLeaveRoomClick(ActionEvent event) {
        countdownHandler.stop();       // Đóng luồng đếm ngược tránh rò rỉ bộ nhớ (Memory Leak)
        client.removeListener(listener); // Hủy đăng ký lắng nghe tin nhắn để giải phóng luồng mạng
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiết sản phẩm"); // Chuyển đổi màn hình giao diện
    }

    /**
     * Khởi tạo cấu hình kết nối các cột dữ liệu thuộc tính của Object BidItem vào bảng TableView
     */
    private void setupTable() {
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colUser.setCellValueFactory(c -> c.getValue().userProperty());
        colAmount.setCellValueFactory(c -> c.getValue().amountProperty());
        tableBidHistory.setItems(bidHistory); // Đồng bộ danh sách theo dõi
        if (listNotifications != null) listNotifications.setItems(notifications);
    }

    /**
     * Thêm một dòng nhật ký sự kiện mới vào khung hiển thị thông báo góc trái màn hình công khai
     */
    public void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        Platform.runLater(() -> notifications.add(0, "[" + time + "] " + message)); // Thêm lên đầu danh sách để dễ đọc bản tin mới nhất
    }

    /**
     * Hàm tiện ích giúp cập nhật nhanh nội dung hiển thị nhãn giá tiền lớn giữa phòng đấu giá
     */
    public void updateCurrentBidLabel(double bid) {
        if (lblCurrentBid != null) lblCurrentBid.setText(String.format("Mức giá hiện tại: %,.0f VNĐ", bid));
    }

    // ── ⚙️ DANH SÁCH CÁ C HÀM GETTER / SETTER GIÚP GIAO TIẾP GIAO TẦNG GIỮA CÁC FILE TÁCH ────────────────
    public Gson getGson() { return gson; }
    public String getItemId() { return itemId; }
    public void setItemId(String id) { this.itemId = id; }
    public Item getCurrentItem() { return currentItem; }
    public void setCurrentItem(Item item) { this.currentItem = item; }
    public long getSecondsRemaining() { return secondsRemaining; }
    public void setSecondsRemaining(long sec) { this.secondsRemaining = sec; }
    public double getLatestBid() { return latestBid; }
    public void setLatestBid(double bid) { this.latestBid = bid; }
    public boolean isHistoryLoaded() { return historyLoaded; }
    public void setHistoryLoaded(boolean state) { this.historyLoaded = state; }
    
    public ObservableList<BidItem> getBidHistoryList() { return bidHistory; }
    public LiveBiddingChartManager getChartManager() { return chartManager; }
    public CountdownHandler getCountdownHandler() { return countdownHandler; }
    public AutoBidHandler getAutoBidHandler() { return autoBidHandler; }
    public Label getLblCountdown() { return lblCountdown; }
    public Label getLblProductName() { return lblProductName; }
    public TextField getTxtBidAmount() { return txtBidAmount; }
    public TextField getTxtMaxBid() { return txtMaxBid; }
    public Button getBtnAutoBid() { return btnAutoBid; }
    public Label getLblAutoBidStatus() { return lblAutoBidStatus; }
    public Label getLblAutoBidInfo() { return lblAutoBidInfo; }
    public HBox getHboxAutoBidInfo() { return hboxAutoBidInfo; }
}