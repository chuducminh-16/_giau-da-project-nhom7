package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.model.Product;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
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
import java.util.List;
import java.util.ResourceBundle;

public class HomeController implements Initializable {

    // ── FXML ────────────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private Label     userLabel;

    // Hero card
    @FXML private Label heroName;
    @FXML private Label heroBid;
    @FXML private Label heroStatus;
    @FXML private Label heroDesc;

    // Bảng
    @FXML private TableView<Product>           auctionTable;
    @FXML private TableColumn<Product, String> colAuctionName;
    @FXML private TableColumn<Product, Double> colAuctionPrice;
    @FXML private TableColumn<Product, String> colAuctionTime;
    @FXML private TableColumn<Product, String> colAuctionStatus;
    @FXML private TableColumn<Product, Void>   colAuctionAction;

    // ── State ───────────────────────────────────────────
    private final ObservableList<Product> auctionList =
            FXCollections.observableArrayList();
    private Product heroProduct; // sản phẩm đang hiện ở hero card

    // ── Tools ───────────────────────────────────────────
    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener =
            this::handleServerMessage;

    // ── initialize ──────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Hiện tên user đã login
        String username = UserSession.getInstance().getUsername();
        if (userLabel != null && username != null)
            userLabel.setText("👤  " + username);

        setupTable();
        client.addListener(listener);

        // Lấy danh sách phiên đang active từ server
        client.send(new Message("GET_AUCTIONS", "{}"));
    }

    // ── Setup bảng ──────────────────────────────────────
    private void setupTable() {
        colAuctionName.setCellValueFactory(
                new PropertyValueFactory<>("name"));

        colAuctionPrice.setCellValueFactory(
                new PropertyValueFactory<>("currentBid"));
        colAuctionPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : String.format("%,.0f VNĐ", item));
            }
        });

        colAuctionTime.setCellValueFactory(
                new PropertyValueFactory<>("formattedEndTime"));

        colAuctionStatus.setCellValueFactory(
                new PropertyValueFactory<>("status"));
        colAuctionStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "ACTIVE" -> "-fx-text-fill:#38a169; -fx-font-weight:bold;";
                    case "SOLD"   -> "-fx-text-fill:#e53e3e; -fx-font-weight:bold;";
                    default       -> "-fx-text-fill:#d69e2e; -fx-font-weight:bold;";
                });
            }
        });

        // Cột hành động — nút "Tham gia"
        colAuctionAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Tham gia");
            {
                btn.setStyle(
                        "-fx-background-color:#48bb78; -fx-text-fill:white;" +
                                "-fx-background-radius:6; -fx-cursor:hand;" +
                                "-fx-font-size:12; -fx-padding:4 12;");
                btn.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    openDetail(p, e);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        auctionTable.setItems(auctionList);
    }

    // ── Nhận message từ server ──────────────────────────
    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            case "AUCTIONS_LIST" -> {
                // Server trả về danh sách sản phẩm active
                AuctionsResponse resp = gson.fromJson(
                        msg.getPayload(), AuctionsResponse.class);

                Platform.runLater(() -> {
                    if (resp.auctions != null && !resp.auctions.isEmpty()) {
                        auctionList.setAll(resp.auctions);
                        // Sản phẩm đầu tiên → hiện lên Hero Card
                        updateHeroCard(resp.auctions.get(0));
                    }
                });
            }

            case "BID_UPDATE" -> {
                // Ai đó vừa bid → cập nhật giá trong bảng realtime
                BidUpdateDto dto = gson.fromJson(
                        msg.getPayload(), BidUpdateDto.class);
                Platform.runLater(() -> {
                    auctionList.stream()
                            .filter(p -> p.getId().equals(dto.productId))
                            .findFirst()
                            .ifPresent(p -> {
                                p.setCurrentBid(dto.newBid);
                                auctionTable.refresh();
                                // Cập nhật hero nếu đang hiện sản phẩm đó
                                if (heroProduct != null &&
                                        heroProduct.getId().equals(dto.productId)) {
                                    updateHeroCard(p);
                                }
                            });
                });
            }
        }
    }

    // ── Cập nhật Hero Card ──────────────────────────────
    private void updateHeroCard(Product p) {
        this.heroProduct = p;
        if (heroName   != null) heroName.setText(p.getName());
        if (heroBid    != null) heroBid.setText(
                String.format("%,.0f VNĐ", p.getCurrentBid()));
        if (heroStatus != null) heroStatus.setText("Lot • " + p.getStatus());
        if (heroDesc   != null) heroDesc.setText(          // ← thêm dòng này
                p.getDescription() != null ? p.getDescription() : "");
    }

    // ── Mở Detail với sản phẩm được chọn ───────────────
    private void openDetail(Product product, ActionEvent event) {
        // Lưu vào Singleton trước khi chuyển màn hình
        SelectedProductSession.getInstance().setProduct(product);
        client.removeListener(listener);
        SceneEngine.changeScene(event,
                "detail-view.fxml", "The Curator - Chi tiết sản phẩm");
    }

    // ── Hero card: bấm Place Bid ────────────────────────
    @FXML
    public void onHeroBidClicked(ActionEvent event) {
        if (heroProduct != null) {
            openDetail(heroProduct, event);
        }
    }

    // ── Refresh ─────────────────────────────────────────
    @FXML
    public void onRefreshAuctions(ActionEvent event) {
        client.send(new Message("GET_AUCTIONS", "{}"));
    }

    // ── Search ──────────────────────────────────────────
    @FXML
    public void onSearchEnter(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER) return;
        String keyword = searchField.getText().trim().toLowerCase();
        if (keyword.isEmpty()) {
            client.send(new Message("GET_AUCTIONS", "{}"));
            return;
        }
        // Lọc local trước — đủ dùng cho demo
        ObservableList<Product> filtered = auctionList.filtered(
                p -> p.getName().toLowerCase().contains(keyword));
        auctionTable.setItems(filtered);
    }

    // ── Sidebar ─────────────────────────────────────────
    @FXML public void onSideAuctionsClick(ActionEvent event) {}

    @FXML
    public void onBidHistoryClick(ActionEvent event) {
        System.out.println("Mở lịch sử đấu giá");
    }

    @FXML
    public void onSellerDashboardClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event,
                "manage-product-view.fxml", "The Curator - Seller Dashboard");
    }

    @FXML
    public void onLogoutClick(ActionEvent event) {
        client.removeListener(listener);
        UserSession.getInstance().logout();
        SelectedProductSession.getInstance().clear();
        SceneEngine.changeScene(event,
                "login-view.fxml", "The Curator - Đăng nhập");
    }

    @FXML
    public void onCardBidClicked(ActionEvent event) {}

    // ── DTOs ─────────────────────────────────────────────
    private record AuctionsResponse(
            boolean success, List<Product> auctions) {}
    private record BidUpdateDto(String productId, double newBid) {}
}