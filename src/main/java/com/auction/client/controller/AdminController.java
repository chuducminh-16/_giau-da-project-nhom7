package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ScrollPane;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * AdminController — điều khiển màn hình Admin Dashboard.
 *
 * Chỉ dành cho user có role ADMIN.
 * Gồm 3 panel:
 *   - panelProducts  : quản lí sản phẩm
 *   - panelUsers     : quản lí tài khoản
 *   - panelAuctions  : quản lí phiên đấu giá
 *
 * Giao tiếp server qua message types:
 *   ADMIN_GET_PRODUCTS, ADMIN_GET_USERS, ADMIN_GET_AUCTIONS,
 *   ADMIN_DELETE_PRODUCT, ADMIN_DELETE_USER, ADMIN_FORCE_CLOSE_AUCTION
 */
public class AdminController implements Initializable {

    // ── FXML: Sidebar buttons ────────────────────────────────────────────
    @FXML private Button btnSideProducts;
    @FXML private Button btnSideUsers;
    @FXML private Button btnSideAuctions;
    @FXML private Label  lblAdminName;
    @FXML private Label  lblStatusBar;

    // Sidebar stats
    @FXML private Label lblStatProducts;
    @FXML private Label lblStatUsers;
    @FXML private Label lblStatAuctions;

    // ── FXML: Panels ─────────────────────────────────────────────────────
    @FXML private ScrollPane panelProducts;
    @FXML private ScrollPane panelUsers;
    @FXML private ScrollPane panelAuctions;

    // ── FXML: Panel Sản phẩm ────────────────────────────────────────────
    @FXML private TableView<Map<String, Object>>          tableProducts;
    @FXML private TableColumn<Map<String, Object>, String> colProdId;
    @FXML private TableColumn<Map<String, Object>, String> colProdName;
    @FXML private TableColumn<Map<String, Object>, String> colProdType;
    @FXML private TableColumn<Map<String, Object>, String> colProdPrice;
    @FXML private TableColumn<Map<String, Object>, String> colProdSeller;
    @FXML private TableColumn<Map<String, Object>, String> colProdStatus;
    @FXML private TableColumn<Map<String, Object>, String> colProdEndTime;
    @FXML private TableColumn<Map<String, Object>, Void>   colProdAction;
    @FXML private TextField txtSearchProduct;
    @FXML private Label     lblTotalProducts;
    @FXML private Label     lblActiveProducts;
    @FXML private Label     lblPendingProducts;
    @FXML private Label     lblProductStatus;

    // ── FXML: Panel Người dùng ───────────────────────────────────────────
    @FXML private TableView<Map<String, Object>>          tableUsers;
    @FXML private TableColumn<Map<String, Object>, String> colUserId;
    @FXML private TableColumn<Map<String, Object>, String> colUsername;
    @FXML private TableColumn<Map<String, Object>, String> colUserEmail;
    @FXML private TableColumn<Map<String, Object>, String> colUserRole;
    @FXML private TableColumn<Map<String, Object>, String> colUserBalance;
    @FXML private TableColumn<Map<String, Object>, Void>   colUserAction;
    @FXML private TextField txtSearchUser;
    @FXML private Label     lblTotalUsers;
    @FXML private Label     lblTotalBidders;
    @FXML private Label     lblTotalSellers;
    @FXML private Label     lblUserStatus;

    // ── FXML: Panel Phiên đấu giá ────────────────────────────────────────
    @FXML private TableView<Map<String, Object>>          tableAuctions;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionId;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionItem;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionSeller;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionPrice;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionStatus;
    @FXML private TableColumn<Map<String, Object>, String> colAuctionEnd;
    @FXML private TableColumn<Map<String, Object>, Void>   colAuctionAction;
    @FXML private Label lblRunningAuctions;
    @FXML private Label lblOpenAuctions;
    @FXML private Label lblFinishedAuctions;
    @FXML private Label lblCanceledAuctions;
    @FXML private Label lblAuctionStatus;

    // Auction filter buttons
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterRunning;
    @FXML private Button btnFilterOpen;
    @FXML private Button btnFilterFinished;

    // ── Data ─────────────────────────────────────────────────────────────
    private final ObservableList<Map<String, Object>> allProducts  = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> allUsers     = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> allAuctions  = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> filteredAuctions = FXCollections.observableArrayList();

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    // ── INITIALIZE ───────────────────────────────────────────────────────

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Hiển thị tên admin đang đăng nhập
        String username = UserSession.getInstance().getUsername();
        if (lblAdminName != null && username != null) {
            lblAdminName.setText("👤 " + username);
        }

        client.addListener(listener);

        setupProductTable();
        setupUserTable();
        setupAuctionTable();

        // Load tất cả dữ liệu ngay khi vào
        loadAllData();

        setStatus("Đang tải dữ liệu...");
    }

    // ── SETUP TABLES ─────────────────────────────────────────────────────

    private void setupProductTable() {
        colProdId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("id"))));
        colProdName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("name"))));
        colProdType.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("type"))));
        colProdPrice.setCellValueFactory(d -> {
            double price = safeDouble(d.getValue().get("current_price"));
            return new javafx.beans.property.SimpleStringProperty(
                    price > 0 ? String.format("%,.0f VNĐ", price) : "--");
        });
        colProdSeller.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("seller_id"))));
        colProdStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("status"))));
        colProdEndTime.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                formatDateTime(safeStr(d.getValue().get("end_time")))));

        // Status column với màu sắc
        colProdStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(getStatusStyle(item));
            }
        });

        // Action column: nút Xóa
        colProdAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = new Button("🗑 Xóa");
            {
                btnDelete.setStyle("-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;" +
                        "-fx-font-size: 11; -fx-padding: 4 10;");
                btnDelete.setOnAction(e -> {
                    Map<String, Object> row = getTableView().getItems().get(getIndex());
                    confirmDeleteProduct(row);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btnDelete);
            }
        });

        tableProducts.setItems(allProducts);
    }

    private void setupUserTable() {
        colUserId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("id"))));
        colUsername.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("username"))));
        colUserEmail.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("email"))));
        colUserRole.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("role"))));
        colUserBalance.setCellValueFactory(d -> {
            double bal = safeDouble(d.getValue().get("balance"));
            return new javafx.beans.property.SimpleStringProperty(
                    String.format("%,.0f VNĐ", bal));
        });

        // Role column với màu
        colUserRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "ADMIN"  -> "-fx-text-fill:#e53e3e; -fx-font-weight:bold;";
                    case "SELLER" -> "-fx-text-fill:#d69e2e; -fx-font-weight:bold;";
                    default       -> "-fx-text-fill:#38a169; -fx-font-weight:bold;";
                });
            }
        });

        // Action column: nút Xóa (không xóa chính mình)
        colUserAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = new Button("🗑 Xóa");
            {
                btnDelete.setStyle("-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;" +
                        "-fx-font-size: 11; -fx-padding: 4 10;");
                btnDelete.setOnAction(e -> {
                    Map<String, Object> row = getTableView().getItems().get(getIndex());
                    confirmDeleteUser(row);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                // Không cho xóa chính mình
                Map<String, Object> row = getTableView().getItems().get(getIndex());
                String rowId = safeStr(row.get("id"));
                String myId  = UserSession.getInstance().getUserId();
                btnDelete.setDisable(rowId.equals(myId));
                setGraphic(btnDelete);
            }
        });

        tableUsers.setItems(allUsers);
    }

    private void setupAuctionTable() {
        colAuctionId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("id"))));
        colAuctionItem.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("itemName"))));
        colAuctionSeller.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("sellerId"))));
        colAuctionPrice.setCellValueFactory(d -> {
            double price = safeDouble(d.getValue().get("currentPrice"));
            return new javafx.beans.property.SimpleStringProperty(
                    price > 0 ? String.format("%,.0f VNĐ", price) : "--");
        });
        colAuctionStatus.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                safeStr(d.getValue().get("status"))));
        colAuctionEnd.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                formatDateTime(safeStr(d.getValue().get("endTime")))));

        // Status column với màu
        colAuctionStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(getStatusStyle(item));
            }
        });

        // Action column: nút Đóng phiên (chỉ RUNNING/OPEN)
        colAuctionAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnClose = new Button("⏹ Đóng");
            {
                btnClose.setStyle("-fx-background-color: #d69e2e; -fx-text-fill: white;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;" +
                        "-fx-font-size: 11; -fx-padding: 4 10;");
                btnClose.setOnAction(e -> {
                    Map<String, Object> row = getTableView().getItems().get(getIndex());
                    confirmCloseAuction(row);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Map<String, Object> row = getTableView().getItems().get(getIndex());
                String status = safeStr(row.get("status"));
                boolean canClose = "RUNNING".equals(status) || "OPEN".equals(status);
                btnClose.setDisable(!canClose);
                setGraphic(btnClose);
            }
        });

        tableAuctions.setItems(filteredAuctions);
    }

    // ── LOAD DATA ────────────────────────────────────────────────────────

    private void loadAllData() {
        loadProducts();
        loadUsers();
        loadAuctions();
    }

    private void loadProducts() {
        client.send(new Message("ADMIN_GET_PRODUCTS", "{}"));
    }

    private void loadUsers() {
        client.send(new Message("ADMIN_GET_USERS", "{}"));
    }

    private void loadAuctions() {
        client.send(new Message("ADMIN_GET_AUCTIONS", "{}"));
    }

    // ── HANDLE SERVER MESSAGE ────────────────────────────────────────────

    private void handleServerMessage(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {

                case "ADMIN_PRODUCTS_RESPONSE" -> {
                    AdminListResponse resp = gson.fromJson(msg.getPayload(), AdminListResponse.class);
                    if (resp.success && resp.data != null) {
                        allProducts.setAll(resp.data);
                        updateProductStats();
                        setStatus("Tải " + resp.data.size() + " sản phẩm thành công.");
                    }
                }

                case "ADMIN_USERS_RESPONSE" -> {
                    AdminListResponse resp = gson.fromJson(msg.getPayload(), AdminListResponse.class);
                    if (resp.success && resp.data != null) {
                        allUsers.setAll(resp.data);
                        updateUserStats();
                        setStatus("Tải " + resp.data.size() + " tài khoản thành công.");
                    }
                }

                case "ADMIN_AUCTIONS_RESPONSE" -> {
                    AdminListResponse resp = gson.fromJson(msg.getPayload(), AdminListResponse.class);
                    if (resp.success && resp.data != null) {
                        allAuctions.setAll(resp.data);
                        filteredAuctions.setAll(allAuctions);
                        updateAuctionStats();
                        setStatus("Tải " + resp.data.size() + " phiên đấu giá thành công.");
                    }
                }

                case "ADMIN_DELETE_PRODUCT_RESPONSE" -> {
                    SimpleResponse resp = gson.fromJson(msg.getPayload(), SimpleResponse.class);
                    if (resp.success) {
                        showProductStatus("✓ " + resp.message, false);
                        loadProducts();
                    } else {
                        showProductStatus("⚠ " + resp.message, true);
                    }
                }

                case "ADMIN_DELETE_USER_RESPONSE" -> {
                    SimpleResponse resp = gson.fromJson(msg.getPayload(), SimpleResponse.class);
                    if (resp.success) {
                        showUserStatus("✓ " + resp.message, false);
                        loadUsers();
                    } else {
                        showUserStatus("⚠ " + resp.message, true);
                    }
                }

                case "ADMIN_CLOSE_AUCTION_RESPONSE" -> {
                    SimpleResponse resp = gson.fromJson(msg.getPayload(), SimpleResponse.class);
                    if (resp.success) {
                        showAuctionStatus("✓ " + resp.message, false);
                        loadAuctions();
                    } else {
                        showAuctionStatus("⚠ " + resp.message, true);
                    }
                }
            }
        });
    }

    // ── UPDATE STATS ─────────────────────────────────────────────────────

    private void updateProductStats() {
        long total   = allProducts.size();
        long active  = allProducts.stream()
                .filter(p -> "RUNNING".equals(p.get("status")) || "OPEN".equals(p.get("status")))
                .count();
        long pending = allProducts.stream()
                .filter(p -> "PENDING".equals(p.get("status")))
                .count();

        setText(lblTotalProducts,   String.valueOf(total));
        setText(lblActiveProducts,  String.valueOf(active));
        setText(lblPendingProducts, String.valueOf(pending));
        setText(lblStatProducts,    "📦 Sản phẩm: " + total);
    }

    private void updateUserStats() {
        long total   = allUsers.size();
        long bidders = allUsers.stream()
                .filter(u -> "BIDDER".equals(u.get("role"))).count();
        long sellers = allUsers.stream()
                .filter(u -> "SELLER".equals(u.get("role"))).count();

        setText(lblTotalUsers,    String.valueOf(total));
        setText(lblTotalBidders,  String.valueOf(bidders));
        setText(lblTotalSellers,  String.valueOf(sellers));
        setText(lblStatUsers,     "👥 Người dùng: " + total);
    }

    private void updateAuctionStats() {
        long running  = allAuctions.stream()
                .filter(a -> "RUNNING".equals(a.get("status"))).count();
        long open     = allAuctions.stream()
                .filter(a -> "OPEN".equals(a.get("status"))).count();
        long finished = allAuctions.stream()
                .filter(a -> "FINISHED".equals(a.get("status")) || "PAID".equals(a.get("status"))).count();
        long canceled = allAuctions.stream()
                .filter(a -> "CANCELED".equals(a.get("status"))).count();

        setText(lblRunningAuctions,  String.valueOf(running));
        setText(lblOpenAuctions,     String.valueOf(open));
        setText(lblFinishedAuctions, String.valueOf(finished));
        setText(lblCanceledAuctions, String.valueOf(canceled));
        setText(lblStatAuctions,     "🔨 Phiên: " + allAuctions.size());
    }

    // ── SIDEBAR NAVIGATION ───────────────────────────────────────────────

    @FXML
    public void onSideProductsClick(ActionEvent event) {
        showPanel("products");
        setSidebarActive(btnSideProducts);
        setStatus("Đang xem: Quản lí Sản phẩm");
    }

    @FXML
    public void onSideUsersClick(ActionEvent event) {
        showPanel("users");
        setSidebarActive(btnSideUsers);
        setStatus("Đang xem: Quản lí Người dùng");
    }

    @FXML
    public void onSideAuctionsClick(ActionEvent event) {
        showPanel("auctions");
        setSidebarActive(btnSideAuctions);
        setStatus("Đang xem: Quản lí Phiên đấu giá");
    }

    private void showPanel(String panel) {
        panelProducts.setVisible(false);  panelProducts.setManaged(false);
        panelUsers.setVisible(false);     panelUsers.setManaged(false);
        panelAuctions.setVisible(false);  panelAuctions.setManaged(false);

        switch (panel) {
            case "products" -> { panelProducts.setVisible(true);  panelProducts.setManaged(true); }
            case "users"    -> { panelUsers.setVisible(true);     panelUsers.setManaged(true); }
            case "auctions" -> { panelAuctions.setVisible(true);  panelAuctions.setManaged(true); }
        }
    }

    private void setSidebarActive(Button active) {
        String inactiveStyle = "-fx-background-color: transparent; -fx-text-fill: #a0aec0;" +
                "-fx-alignment: CENTER_LEFT; -fx-background-radius: 6;" +
                "-fx-padding: 10 14; -fx-font-size: 13; -fx-cursor: hand;";
        String activeStyle = "-fx-background-color: #4299e1; -fx-text-fill: white;" +
                "-fx-alignment: CENTER_LEFT; -fx-background-radius: 6;" +
                "-fx-padding: 10 14; -fx-font-size: 13; -fx-cursor: hand;";
        btnSideProducts.setStyle(inactiveStyle);
        btnSideUsers.setStyle(inactiveStyle);
        btnSideAuctions.setStyle(inactiveStyle);
        active.setStyle(activeStyle);
    }

    // ── REFRESH ──────────────────────────────────────────────────────────

    @FXML public void onRefreshAll(ActionEvent event) {
        loadAllData();
        setStatus("Đang tải lại dữ liệu...");
    }

    @FXML public void onRefreshProducts(ActionEvent event) {
        loadProducts();
        setStatus("Đang tải lại sản phẩm...");
    }

    @FXML public void onRefreshUsers(ActionEvent event) {
        loadUsers();
        setStatus("Đang tải lại người dùng...");
    }

    @FXML public void onRefreshAuctions(ActionEvent event) {
        loadAuctions();
        setStatus("Đang tải lại phiên đấu giá...");
    }

    // ── SEARCH ───────────────────────────────────────────────────────────

    @FXML
    public void onSearchProduct(KeyEvent event) {
        String keyword = txtSearchProduct.getText().trim().toLowerCase();
        if (keyword.isEmpty()) {
            tableProducts.setItems(allProducts);
        } else {
            ObservableList<Map<String, Object>> filtered = allProducts.filtered(
                    p -> safeStr(p.get("name")).toLowerCase().contains(keyword) ||
                            safeStr(p.get("id")).toLowerCase().contains(keyword) ||
                            safeStr(p.get("seller_id")).toLowerCase().contains(keyword)
            );
            tableProducts.setItems(filtered);
        }
    }

    @FXML
    public void onSearchUser(KeyEvent event) {
        String keyword = txtSearchUser.getText().trim().toLowerCase();
        if (keyword.isEmpty()) {
            tableUsers.setItems(allUsers);
        } else {
            ObservableList<Map<String, Object>> filtered = allUsers.filtered(
                    u -> safeStr(u.get("username")).toLowerCase().contains(keyword) ||
                            safeStr(u.get("email")).toLowerCase().contains(keyword) ||
                            safeStr(u.get("role")).toLowerCase().contains(keyword)
            );
            tableUsers.setItems(filtered);
        }
    }

    // ── AUCTION FILTER ───────────────────────────────────────────────────

    @FXML
    public void onFilterAll(ActionEvent event) {
        filteredAuctions.setAll(allAuctions);
        setFilterButtonActive(btnFilterAll);
    }

    @FXML
    public void onFilterRunning(ActionEvent event) {
        filteredAuctions.setAll(allAuctions.filtered(
                a -> "RUNNING".equals(a.get("status"))));
        setFilterButtonActive(btnFilterRunning);
    }

    @FXML
    public void onFilterOpen(ActionEvent event) {
        filteredAuctions.setAll(allAuctions.filtered(
                a -> "OPEN".equals(a.get("status"))));
        setFilterButtonActive(btnFilterOpen);
    }

    @FXML
    public void onFilterFinished(ActionEvent event) {
        filteredAuctions.setAll(allAuctions.filtered(
                a -> "FINISHED".equals(a.get("status")) || "PAID".equals(a.get("status"))));
        setFilterButtonActive(btnFilterFinished);
    }

    private void setFilterButtonActive(Button active) {
        String inactive = "-fx-background-color: #edf2f7; -fx-text-fill: #4a5568;" +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12;";
        String activeStyle = "-fx-background-color: #1a1f2e; -fx-text-fill: white;" +
                "-fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12;";
        btnFilterAll.setStyle(inactive);
        btnFilterRunning.setStyle(inactive);
        btnFilterOpen.setStyle(inactive);
        btnFilterFinished.setStyle(inactive);
        active.setStyle(activeStyle);
    }

    // ── DELETE / CLOSE ACTIONS ───────────────────────────────────────────

    private void confirmDeleteProduct(Map<String, Object> row) {
        String name = safeStr(row.get("name"));
        String id   = safeStr(row.get("id"));
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Xóa sản phẩm: " + name + "?\n\nThao tác này sẽ xóa cả phiên và lịch sử bid.",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận xóa sản phẩm");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("ADMIN_DELETE_PRODUCT",
                        gson.toJson(Map.of("productId", id))));
                showProductStatus("Đang xóa...", false);
            }
        });
    }

    private void confirmDeleteUser(Map<String, Object> row) {
        String username = safeStr(row.get("username"));
        String id       = safeStr(row.get("id"));
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Xóa tài khoản: " + username + "?\n\nThao tác này không thể hoàn tác.",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận xóa tài khoản");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("ADMIN_DELETE_USER",
                        gson.toJson(Map.of("userId", id))));
                showUserStatus("Đang xóa...", false);
            }
        });
    }

    private void confirmCloseAuction(Map<String, Object> row) {
        String itemName = safeStr(row.get("itemName"));
        Object idObj    = row.get("id");
        String id       = idObj != null ? idObj.toString() : "";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Đóng phiên: " + itemName + "?\n\nPhiên sẽ kết thúc ngay, winner được xác định.",
                ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận đóng phiên đấu giá");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("ADMIN_FORCE_CLOSE_AUCTION",
                        gson.toJson(Map.of("auctionId", id))));
                showAuctionStatus("Đang đóng phiên...", false);
            }
        });
    }

    // ── LOGOUT ───────────────────────────────────────────────────────────

    @FXML
    public void onLogoutClick(ActionEvent event) {
        client.removeListener(listener);
        UserSession.getInstance().logout();
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }

    // ── HELPERS ──────────────────────────────────────────────────────────

    private String safeStr(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private double safeDouble(Object obj) {
        if (obj == null) return 0;
        try { return Double.parseDouble(obj.toString()); }
        catch (Exception e) { return 0; }
    }

    /**
     * Cắt ngắn datetime DB (có thể có ký tự 'T' hoặc space) thành "dd/MM/yyyy HH:mm".
     */
    private String formatDateTime(String raw) {
        if (raw == null || raw.isBlank()) return "--";
        try {
            // Chuẩn hóa: "2026-12-31T23:59:59" hoặc "2026-12-31 23:59:59"
            String normalized = raw.replace("T", " ").substring(0, Math.min(16, raw.length()));
            // normalized = "2026-12-31 23:59"
            String[] parts = normalized.split("[\\-\\s:]");
            if (parts.length >= 5) {
                return parts[2] + "/" + parts[1] + "/" + parts[0]
                        + " " + parts[3] + ":" + parts[4];
            }
        } catch (Exception ignored) {}
        return raw;
    }

    private String getStatusStyle(String status) {
        return switch (status) {
            case "RUNNING"  -> "-fx-text-fill:#fc8181; -fx-font-weight:bold;";
            case "OPEN"     -> "-fx-text-fill:#4299e1; -fx-font-weight:bold;";
            case "FINISHED",
                 "PAID"     -> "-fx-text-fill:#38a169; -fx-font-weight:bold;";
            case "PENDING"  -> "-fx-text-fill:#d69e2e; -fx-font-weight:bold;";
            default         -> "-fx-text-fill:#718096; -fx-font-weight:bold;";
        };
    }

    private void setStatus(String msg) {
        if (lblStatusBar != null) lblStatusBar.setText(msg);
    }

    private void setText(Label lbl, String text) {
        if (lbl != null) lbl.setText(text);
    }

    private void showProductStatus(String msg, boolean isError) {
        showStatus(lblProductStatus, msg, isError);
    }

    private void showUserStatus(String msg, boolean isError) {
        showStatus(lblUserStatus, msg, isError);
    }

    private void showAuctionStatus(String msg, boolean isError) {
        showStatus(lblAuctionStatus, msg, isError);
    }

    private void showStatus(Label lbl, String msg, boolean isError) {
        if (lbl == null) return;
        lbl.setText(msg);
        lbl.setStyle(isError
                ? "-fx-text-fill:#e53e3e; -fx-font-size:12px;"
                : "-fx-text-fill:#38a169; -fx-font-size:12px;");
        lbl.setVisible(true);
        lbl.setManaged(true);
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────
    private record AdminListResponse(boolean success, String message,
                                     List<Map<String, Object>> data) {}
    private record SimpleResponse(boolean success, String message) {}
}
