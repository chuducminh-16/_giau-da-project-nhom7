package com.auction.client;

import com.auction.client.model.Product;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    // ══════════════ FXML — Navbar ══════════════
    @FXML private TextField searchField;
    @FXML private Label     userLabel;

    // ══════════════ FXML — Bảng đấu giá ══════════════
    @FXML private TableView<Product>           auctionTable;
    @FXML private TableColumn<Product, String> colAuctionName;
    @FXML private TableColumn<Product, Double> colAuctionPrice;
    @FXML private TableColumn<Product, String> colAuctionTime;
    @FXML private TableColumn<Product, String> colAuctionStatus;
    @FXML private TableColumn<Product, Void>   colAuctionAction;

    private final ObservableList<Product> auctionList = FXCollections.observableArrayList();

    // ══════════════════════════════════════════
    // KHỞI TẠO
    // ══════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("Trang chủ đã tải xong!");
        setupAuctionTable();
        loadMockData();
    }

    // ──────────────────────────────────────────
    // Setup bảng phiên đấu giá
    // ──────────────────────────────────────────
    private void setupAuctionTable() {
        colAuctionName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colAuctionPrice.setCellValueFactory(new PropertyValueFactory<>("currentBid"));
        colAuctionTime.setCellValueFactory(new PropertyValueFactory<>("formattedEndTime"));
        colAuctionStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Cột Hành động: nút "Tham gia" style xanh → detail-view
        colAuctionAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Tham gia");
            {
                btn.setStyle(
                        "-fx-background-color: #48bb78; -fx-text-fill: white;" +
                                "-fx-background-radius: 6; -fx-cursor: hand;" +
                                "-fx-font-size: 12; -fx-padding: 4 12;"
                );
                btn.setOnAction(e ->
                        SceneEngine.changeScene(e, "detail-view.fxml",
                                "The Curator - Chi tiết sản phẩm")
                );
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        // Màu cột Trạng thái
        colAuctionStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                switch (item) {
                    case "Đang diễn ra" ->
                            setStyle("-fx-text-fill: #38a169; -fx-font-weight: bold;");
                    case "Sắp diễn ra" ->
                            setStyle("-fx-text-fill: #d69e2e; -fx-font-weight: bold;");
                    default ->
                            setStyle("-fx-text-fill: #e53e3e; -fx-font-weight: bold;");
                }
            }
        });

        auctionTable.setItems(auctionList);
    }

    /** Mock data — thay bằng gọi server sau */
    private void loadMockData() {
        auctionList.add(new Product(
                "The Eames Prototype Archive", 68.68, 5.0, "Mô tả",
                "Đang diễn ra",
                LocalDateTime.now(), "",
                LocalDateTime.now().plusHours(2)));
        auctionList.add(new Product(
                "Đồng hồ Rolex Daytona", 1000.0, 100.0, "Mô tả",
                "Đang diễn ra",
                LocalDateTime.now(), "",
                LocalDateTime.now().plusHours(3)));
        auctionList.add(new Product(
                "Tranh sơn dầu thế kỷ 19", 500.0, 50.0, "Mô tả",
                "Sắp diễn ra",
                LocalDateTime.now().plusDays(1), "",
                LocalDateTime.now().plusDays(5)));
    }

    // ══════════════════════════════════════════

    /** Tìm kiếm khi bấm Enter */
    @FXML
    public void onSearchEnter(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String keyword = searchField.getText();
            System.out.println("Tìm kiếm: " + keyword);
            // TODO: gọi API / lọc danh sách
        }
    }

    /** Place Bid (Hero Section) → detail-view */
    @FXML
    public void onHeroBidClicked(ActionEvent event) {
        System.out.println("Đã bấm Place Bid");
        SceneEngine.changeScene(event, "detail-view.fxml",
                "The Curator - Chi tiết sản phẩm");
    }

    /** Bid Now (card nhỏ) */
    @FXML
    public void onCardBidClicked(ActionEvent event) {
        Button clicked = (Button) event.getSource();
        System.out.println("Bấm đấu giá tại: " + clicked.getId());
        // TODO: mở popup hoặc chuyển trang chi tiết
    }

    /** Logout → login-view */
    @FXML
    public void onLogoutClick(ActionEvent event) {
        System.out.println("Đang đăng xuất...");
        SceneEngine.changeScene(event, "login-view.fxml",
                "The Curator - Đăng nhập");
    }

    /** Đăng sản phẩm → manage-product-view */
    @FXML
    public void onSellerDashboardClick(ActionEvent event) {
        SceneEngine.changeScene(event, "manage-product-view.fxml",
                "The Curator - Seller Dashboard");
    }

    // ══════════════════════════════════════════
    // CHỨC NĂNG (sidebar + bảng)
    // ══════════════════════════════════════════

    /** Sidebar: Danh sách đấu giá (trang hiện tại, placeholder) */
    @FXML
    public void onSideAuctionsClick(ActionEvent event) {
        System.out.println("Đang ở trang Danh sách đấu giá");
    }

    /** Sidebar: Lịch sử đấu giá */
    @FXML
    public void onBidHistoryClick(ActionEvent event) {
        System.out.println("Mở lịch sử đấu giá");
        // TODO: SceneEngine.changeScene(event, "bid-history-view.fxml", "...");
    }

    /** Nút Làm mới bảng */
    @FXML
    public void onRefreshAuctions(ActionEvent event) {
        System.out.println("Làm mới danh sách phiên đấu giá");
        // TODO: gọi server lấy danh sách mới, rồi auctionList.setAll(...)
    }
}

