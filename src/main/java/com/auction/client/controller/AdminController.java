package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.handler.admin.AdminMessageHandler;
import com.auction.client.handler.admin.AdminTableSetup;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * 👑 LỚP ĐIỀU KHIỂN DASHBOARD ADMIN (ADMIN UI CONTROLLER)
 */
public class AdminController implements Initializable {

    // ── FXML: Khai báo thành phần Sidebar & Thanh trạng thái ─────────────────
    @FXML private Button btnSideProducts;
    @FXML private Button btnSideUsers;
    @FXML private Button btnSideAuctions;
    @FXML private Label  lblAdminName;
    @FXML private Label  lblStatusBar;

    // ✅ THÊM MỚI: Nút ACC trên navbar
    @FXML private Button btnAccount;

    // Các thẻ thống kê nhanh ghim trên Sidebar
    @FXML private Label lblStatProducts;
    @FXML private Label lblStatUsers;
    @FXML private Label lblStatAuctions;

    // ── FXML: Các phân vùng Panel giao diện chính ───────────────────────────
    @FXML private ScrollPane panelProducts;
    @FXML private ScrollPane panelUsers;
    @FXML private ScrollPane panelAuctions;

    // ── FXML: Phân vùng quản lý Sản phẩm ─────────────────────────────────────
    @FXML private TableView<Map<String, Object>>           tableProducts;
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

    // ── FXML: Phân vùng quản lý Người dùng ────────────────────────────────────
    @FXML private TableView<Map<String, Object>>           tableUsers;
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

    // ── FXML: Phân vùng quản lý các Phiên Đấu Giá ──────────────────────────────
    @FXML private TableView<Map<String, Object>>           tableAuctions;
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

    // Các nút bấm lọc nhanh trạng thái phiên đấu giá
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterRunning;
    @FXML private Button btnFilterOpen;
    @FXML private Button btnFilterFinished;

    // ── 📦 DANH SÁCH DỮ LIỆU QUAN SÁT (OBSERVABLE LISTS) ─────────────────────
    private final ObservableList<Map<String, Object>> allProducts      = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> allUsers         = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> allAuctions      = FXCollections.observableArrayList();
    private final ObservableList<Map<String, Object>> filteredAuctions = FXCollections.observableArrayList();

    private final NetworkClient client = NetworkClient.getInstance();
    private AdminMessageHandler messageHandler;
    private NetworkClient.MessageListener listener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String username = UserSession.getInstance().getUsername();
        if (lblAdminName != null && username != null) {
            lblAdminName.setText("👤 " + username);
        }

        this.messageHandler = new AdminMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerMessage(msg);
        client.addListener(listener);

        AdminTableSetup.setupProductTable(this, tableProducts, colProdId, colProdName, colProdType, colProdPrice, colProdSeller, colProdStatus, colProdEndTime, colProdAction);
        AdminTableSetup.setupUserTable(this, tableUsers, colUserId, colUsername, colUserEmail, colUserRole, colUserBalance, colUserAction);
        AdminTableSetup.setupAuctionTable(this, tableAuctions, colAuctionId, colAuctionItem, colAuctionSeller, colAuctionPrice, colAuctionStatus, colAuctionEnd, colAuctionAction);

        tableProducts.setItems(allProducts);
        tableUsers.setItems(allUsers);
        tableAuctions.setItems(filteredAuctions);

        loadAllData();
        setStatus("Đang tải dữ liệu...");
    }

    // ── 🌐 PHÁT LỆNH YÊU CẦU DỮ LIỆU LÊN SERVER ──────────────────────────────
    public void loadAllData() { loadProducts(); loadUsers(); loadAuctions(); }
    public void loadProducts() { client.send(new Message("ADMIN_GET_PRODUCTS", "{}")); }
    public void loadUsers() { client.send(new Message("ADMIN_GET_USERS", "{}")); }
    public void loadAuctions() { client.send(new Message("ADMIN_GET_AUCTIONS", "{}")); }

    // ── 🔀 ĐIỀU HƯỚNG PANEL & SIDEBAR EFFECT ──────────────────────────────────
    @FXML public void onSideProductsClick(ActionEvent e) { showPanel("products"); setSidebarActive(btnSideProducts); setStatus("Đang xem: Quản lí Sản phẩm"); }
    @FXML public void onSideUsersClick(ActionEvent e) { showPanel("users"); setSidebarActive(btnSideUsers); setStatus("Đang xem: Quản lí Người dùng"); }
    @FXML public void onSideAuctionsClick(ActionEvent e) { showPanel("auctions"); setSidebarActive(btnSideAuctions); setStatus("Đang xem: Quản lí Phiên đấu giá"); }

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
        String off = "-fx-background-color: transparent; -fx-text-fill: #a0aec0; -fx-alignment: CENTER_LEFT; -fx-background-radius: 6; -fx-padding: 10 14; -fx-font-size: 13; -fx-cursor: hand;";
        String on  = "-fx-background-color: #4299e1; -fx-text-fill: white; -fx-alignment: CENTER_LEFT; -fx-background-radius: 6; -fx-padding: 10 14; -fx-font-size: 13; -fx-cursor: hand;";
        btnSideProducts.setStyle(off); btnSideUsers.setStyle(off); btnSideAuctions.setStyle(off); active.setStyle(on);
    }

    // ── 🔄 LÀM MỚI VÀ TÌM KIẾM NHANH ──────────────────────────────────────────
    @FXML public void onRefreshAll(ActionEvent e) { loadAllData(); setStatus("Đang tải lại dữ liệu..."); }
    @FXML public void onRefreshProducts(ActionEvent e) { loadProducts(); setStatus("Đang tải lại sản phẩm..."); }
    @FXML public void onRefreshUsers(ActionEvent e) { loadUsers(); setStatus("Đang tải lại người dùng..."); }
    @FXML public void onRefreshAuctions(ActionEvent e) { loadAuctions(); setStatus("Đang tải lại phiên đấu giá..."); }

    @FXML public void onSearchProduct(KeyEvent event) {
        String kw = txtSearchProduct.getText().trim().toLowerCase();
        if (kw.isEmpty()) tableProducts.setItems(allProducts);
        else tableProducts.setItems(allProducts.filtered(p -> safeStr(p.get("name")).toLowerCase().contains(kw) || safeStr(p.get("id")).toLowerCase().contains(kw) || safeStr(p.get("seller_id")).toLowerCase().contains(kw)));
    }

    @FXML public void onSearchUser(KeyEvent event) {
        String kw = txtSearchUser.getText().trim().toLowerCase();
        if (kw.isEmpty()) tableUsers.setItems(allUsers);
        else tableUsers.setItems(allUsers.filtered(u -> safeStr(u.get("username")).toLowerCase().contains(kw) || safeStr(u.get("email")).toLowerCase().contains(kw) || safeStr(u.get("role")).toLowerCase().contains(kw)));
    }

    // ── ⏳ BỘ LỌC TRẠNG THÁI PHIÊN ĐẤU GIÁ ────────────────────────────────────
    @FXML public void onFilterAll(ActionEvent e) { filteredAuctions.setAll(allAuctions); setFilterButtonActive(btnFilterAll); }
    @FXML public void onFilterRunning(ActionEvent e) { filteredAuctions.setAll(allAuctions.filtered(a -> "RUNNING".equals(a.get("status")))); setFilterButtonActive(btnFilterRunning); }
    @FXML public void onFilterOpen(ActionEvent e) { filteredAuctions.setAll(allAuctions.filtered(a -> "OPEN".equals(a.get("status")))); setFilterButtonActive(btnFilterOpen); }
    @FXML public void onFilterFinished(ActionEvent e) { filteredAuctions.setAll(allAuctions.filtered(a -> "FINISHED".equals(a.get("status")) || "PAID".equals(a.get("status")))); setFilterButtonActive(btnFilterFinished); }

    private void setFilterButtonActive(Button active) {
        String off = "-fx-background-color: #edf2f7; -fx-text-fill: #4a5568; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12;";
        String on  = "-fx-background-color: #1a1f2e; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand; -fx-font-size: 12;";
        btnFilterAll.setStyle(off); btnFilterRunning.setStyle(off); btnFilterOpen.setStyle(off); btnFilterFinished.setStyle(off); active.setStyle(on);
    }

    // ── ❓ THÔNG BÁO XÁC NHẬN CÁC THAO TÁC NGUY HIỂM ──────────────────────────
    public void confirmDeleteProduct(Map<String, Object> row) {
        String name = safeStr(row.get("name")); String id = safeStr(row.get("id"));
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Xóa sản phẩm: " + name + "?\n\nThao tác này sẽ xóa cả phiên và lịch sử bid.", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận xóa");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("ADMIN_DELETE_PRODUCT", messageHandler.getGson().toJson(Map.of("productId", id))));
                showProductStatus("Đang xóa...", false);
            }
        });
    }

    public void confirmDeleteUser(Map<String, Object> row) {
        String username = safeStr(row.get("username")); String id = safeStr(row.get("id"));
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Xóa tài khoản: " + username + "?\n\nThao tác này không thể hoàn tác.", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận xóa tài khoản");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("ADMIN_DELETE_USER", messageHandler.getGson().toJson(Map.of("userId", id))));
                showUserStatus("Đang xóa...", false);
            }
        });
    }

    public void confirmCloseAuction(Map<String, Object> row) {
        String itemName = safeStr(row.get("itemName")); Object idObj = row.get("id");
        String id = idObj != null ? idObj.toString().replaceAll("\\.0$", "") : "";
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Đóng phiên: " + itemName + "?\n\nPhiên sẽ kết thúc ngay, winner được xác định.", ButtonType.YES, ButtonType.NO);
        alert.setTitle("Xác nhận đóng phiên");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("ADMIN_FORCE_CLOSE_AUCTION", messageHandler.getGson().toJson(Map.of("auctionId", id))));
                showAuctionStatus("Đang đóng phiên...", false);
            }
        });
    }

    // ── ✅ THÊM MỚI: Handler nút ACC - Mở trang hồ sơ Admin ───────────────────
    @FXML
    public void onAccountClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "profile-view.fxml", "The Curator — Hồ sơ cá nhân");
    }

    @FXML public void onLogoutClick(ActionEvent event) {
        client.removeListener(listener);
        UserSession.getInstance().logout();
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }

    // ── 🛠️ TIỆN ÍCH ĐỊNH DẠNG ──────────────────────────────────────────────────
    public String safeStr(Object obj) { return obj != null ? obj.toString() : ""; }
    public double safeDouble(Object obj) { if (obj == null) return 0; try { return Double.parseDouble(obj.toString()); } catch (Exception e) { return 0; } }

    public String formatDateTime(String raw) {
        if (raw == null || raw.isBlank()) return "--";
        try {
            String normalized = raw.replace("T", " ").substring(0, Math.min(16, raw.length()));
            String[] parts = normalized.split("[\\-\\s:]");
            if (parts.length >= 5) return parts[2] + "/" + parts[1] + "/" + parts[0] + " " + parts[3] + ":" + parts[4];
        } catch (Exception ignored) {}
        return raw;
    }

    public String getStatusStyle(String status) {
        return switch (status) {
            case "RUNNING"          -> "-fx-text-fill:#fc8181; -fx-font-weight:bold;";
            case "OPEN"             -> "-fx-text-fill:#4299e1; -fx-font-weight:bold;";
            case "FINISHED", "PAID" -> "-fx-text-fill:#38a169; -fx-font-weight:bold;";
            case "PENDING"          -> "-fx-text-fill:#d69e2e; -fx-font-weight:bold;";
            default                 -> "-fx-text-fill:#718096; -fx-font-weight:bold;";
        };
    }

    // ── ⚙️ GETTERS & SETTERS (ĐƯỢC GỌI TỪ HANDLER) ────────────────────────────
    public void setStatus(String msg) { if (lblStatusBar != null) lblStatusBar.setText(msg); }
    public void setLabelText(Label lbl, String text) { if (lbl != null) lbl.setText(text); }
    public void showProductStatus(String msg, boolean err) { showStatus(lblProductStatus, msg, err); }
    public void showUserStatus(String msg, boolean err) { showStatus(lblUserStatus, msg, err); }
    public void showAuctionStatus(String msg, boolean err) { showStatus(lblAuctionStatus, msg, err); }

    private void showStatus(Label lbl, String msg, boolean isError) {
        if (lbl == null) return; lbl.setText(msg);
        lbl.setStyle(isError ? "-fx-text-fill:#e53e3e; -fx-font-size:12px;" : "-fx-text-fill:#38a169; -fx-font-size:12px;");
        lbl.setVisible(true); lbl.setManaged(true);
    }

    public ObservableList<Map<String, Object>> getAllProducts() { return allProducts; }
    public ObservableList<Map<String, Object>> getAllUsers() { return allUsers; }
    public ObservableList<Map<String, Object>> getAllAuctions() { return allAuctions; }
    public ObservableList<Map<String, Object>> getFilteredAuctions() { return filteredAuctions; }

    public Label getLblTotalProducts() { return lblTotalProducts; }
    public Label getLblActiveProducts() { return lblActiveProducts; }
    public Label getLblPendingProducts() { return lblPendingProducts; }
    public Label getLblStatProducts() { return lblStatProducts; }
    public Label getLblTotalUsers() { return lblTotalUsers; }
    public Label getLblTotalBidders() { return lblTotalBidders; }
    public Label getLblTotalSellers() { return lblTotalSellers; }
    public Label getLblStatUsers() { return lblStatUsers; }
    public Label getLblRunningAuctions() { return lblRunningAuctions; }
    public Label getLblOpenAuctions() { return lblOpenAuctions; }
    public Label getLblFinishedAuctions() { return lblFinishedAuctions; }
    public Label getLblCanceledAuctions() { return lblCanceledAuctions; }
    public Label getLblStatAuctions() { return lblStatAuctions; }
}
