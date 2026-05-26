package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.handler.bidhistory.BidHistoryMessageHandler;
import com.auction.client.handler.bidhistory.BidHistoryTableSetup;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
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

public class BidHistoryController implements Initializable {

    // ── FXML Các nhãn hiển thị chung ─────────────────────────────────────────
    @FXML private Label lblUsername;
    @FXML private Label lblStatusBar;
    @FXML private Label lblStatus;

    // Các nút bấm bộ lọc Sidebar Left
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterActive;
    @FXML private Button btnFilterWon;
    @FXML private Button btnFilterLost;

    // Văn bản hiển thị số liệu nhỏ dưới các nút bấm lọc
    @FXML private Label lblStatTotal;
    @FXML private Label lblStatWon;
    @FXML private Label lblStatLost;
    @FXML private Label lblStatActive;

    // Các thẻ Card lớn hiển thị thống kê trung tâm
    @FXML private Label cardTotal;
    @FXML private Label cardWon;
    @FXML private Label cardLost;
    @FXML private Label cardTotalBid;

    @FXML private TextField txtSearch;

    // ── FXML Bảng hiển thị ──────────────────────────────────────────────────
    @FXML private TableView<BidRecord>           historyTable;
    @FXML private TableColumn<BidRecord, String> colItemName;
    @FXML private TableColumn<BidRecord, String> colMyBid;
    @FXML private TableColumn<BidRecord, String> colFinalPrice;
    @FXML private TableColumn<BidRecord, String> colStatus;
    @FXML private TableColumn<BidRecord, String> colEndTime;
    @FXML private TableColumn<BidRecord, String> colSeller;

    // Danh sách lưu trữ bộ nhớ đệm
    private final ObservableList<BidRecord> allRecords  = FXCollections.observableArrayList();
    private final ObservableList<BidRecord> displayData = FXCollections.observableArrayList();

    private final NetworkClient client = NetworkClient.getInstance();
    private BidHistoryMessageHandler messageHandler; // Khai báo Handler
    private NetworkClient.MessageListener listener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String username = UserSession.getInstance().getUsername();
        if (lblUsername != null && username != null) {
            lblUsername.setText("👤 " + username);
        }

        // Tạo liên kết Handler tin nhắn mạng
        this.messageHandler = new BidHistoryMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerMessage(msg);
        client.addListener(listener);

        // Gọi Helper dựng cấu trúc bảng độc lập sang file Setup mới tách
        BidHistoryTableSetup.setupTable(this, historyTable, colItemName, colMyBid, colFinalPrice, colStatus, colEndTime, colSeller);

        historyTable.setItems(displayData);
        loadHistory();
        setStatus("Đang tải lịch sử...");
    }

    public void loadHistory() {
        String bidderId = UserSession.getInstance().getUserId();
        client.send(new Message("GET_USER_BID_HISTORY", new Gson().toJson(Map.of("bidderId", bidderId))));
    }

    // ── ⏳ SỰ KIỆN LỌC DỮ LIỆU TRÊN SIDEBAR ───────────────────────────────────

    @FXML public void onFilterAll(ActionEvent event) {
        displayData.setAll(allRecords); setSidebarActive(btnFilterAll); applySearch();
    }

    @FXML public void onFilterActive(ActionEvent event) {
        setSidebarActive(btnFilterActive);
        displayData.setAll(allRecords.filtered(r -> ("RUNNING".equals(r.status()) || "OPEN".equals(r.status())) && !messageHandler.isExpired(r.endTime())));
        applySearch();
    }

    @FXML public void onFilterWon(ActionEvent event) {
        setSidebarActive(btnFilterWon);
        String myId = UserSession.getInstance().getUserId();
        displayData.setAll(allRecords.filtered(r -> myId.equals(r.winnerId()) && (messageHandler.isFinishedStatus(r.status()) || messageHandler.isExpired(r.endTime()))));
        applySearch();
    }

    @FXML public void onFilterLost(ActionEvent event) {
        setSidebarActive(btnFilterLost);
        String myId = UserSession.getInstance().getUserId();
        displayData.setAll(allRecords.filtered(r -> (messageHandler.isFinishedStatus(r.status()) || messageHandler.isExpired(r.endTime())) && !myId.equals(r.winnerId())));
        applySearch();
    }

    @FXML public void onSearch(KeyEvent event) { applySearch(); }

    /** Thực hiện chức năng tìm kiếm kết hợp điều kiện bộ lọc hiện tại */
    private void applySearch() {
        String kw = txtSearch.getText().trim().toLowerCase();
        String myId = UserSession.getInstance().getUserId();
        ObservableList<BidRecord> baseList = FXCollections.observableArrayList();

        // 1. Xác định tập dữ liệu nền theo màu nút active hiện tại
        if (btnFilterAll.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords);
        } else if (btnFilterActive.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r -> ("RUNNING".equals(r.status()) || "OPEN".equals(r.status())) && !messageHandler.isExpired(r.endTime())));
        } else if (btnFilterWon.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r -> myId.equals(r.winnerId()) && (messageHandler.isFinishedStatus(r.status()) || messageHandler.isExpired(r.endTime()))));
        } else if (btnFilterLost.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r -> (messageHandler.isFinishedStatus(r.status()) || messageHandler.isExpired(r.endTime())) && !myId.equals(r.winnerId())));
        }

        if (kw.isEmpty()) { displayData.setAll(baseList); return; }

        // 2. Lọc chuỗi theo từ khóa người dùng nhập vào ô tìm kiếm
        displayData.setAll(baseList.filtered(r -> r.itemName().toLowerCase().contains(kw) || r.sellerName().toLowerCase().contains(kw)));
    }

    @FXML public void onRefresh(ActionEvent event) {
        txtSearch.clear(); setSidebarActive(btnFilterAll); loadHistory(); setStatus("Đang tải lại...");
    }

    @FXML public void onBackClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    private void setSidebarActive(Button active) {
        String off = "-fx-background-color:transparent; -fx-text-fill:#a0aec0; -fx-alignment:CENTER_LEFT; -fx-background-radius:6; -fx-padding:10 14; -fx-font-size:13; -fx-cursor:hand;";
        String on  = "-fx-background-color:#4299e1; -fx-text-fill:white; -fx-alignment:CENTER_LEFT; -fx-background-radius:6; -fx-padding:10 14; -fx-font-size:13; -fx-cursor:hand;";
        btnFilterAll.setStyle(off); btnFilterActive.setStyle(off); btnFilterWon.setStyle(off); btnFilterLost.setStyle(off);
        active.setStyle(on);
    }

    /** Định dạng chuỗi ngày tháng thô từ server sang kiểu d/m/y dễ nhìn */
    public String formatDateTime(String raw) {
        if (raw == null || raw.isBlank()) return "--";
        try {
            String n = raw.replace("T", " ").substring(0, Math.min(16, raw.length()));
            String[] p = n.split("[\\-\\s:]");
            if (p.length >= 5) return p[2] + "/" + p[1] + "/" + p[0] + " " + p[3] + ":" + p[4];
        } catch (Exception ignored) {}
        return raw;
    }

    // ── ⚙️ GETTERS, SETTERS & UI ACCESSORS (Giao tiếp với Handler/Setup) ─────
    public void setLabelText(Label lbl, String text) { if (lbl != null) lbl.setText(text); }
    public void setStatus(String msg) { if (lblStatusBar != null) lblStatusBar.setText(msg); }
    public BidHistoryMessageHandler getMessageHandler() { return messageHandler; }
    
    public ObservableList<BidRecord> getAllRecords() { return allRecords; }
    public ObservableList<BidRecord> getDisplayData() { return displayData; }

    public Label getCardTotal() { return cardTotal; }
    public Label getCardWon() { return cardWon; }
    public Label getCardLost() { return cardLost; }
    public Label getCardTotalBid() { return cardTotalBid; }
    public Label getLblStatTotal() { return lblStatTotal; }
    public Label getLblStatWon() { return lblStatWon; }
    public Label getLblStatLost() { return lblStatLost; }
    public Label getLblStatActive() { return lblStatActive; }

    public void showError(String msg) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle("-fx-text-fill:#e53e3e; -fx-font-size:12px;");
        lblStatus.setVisible(true); lblStatus.setManaged(true);
    }

    // ── DTO RECORD ───────────────────────────────────────────────────────────
    public record BidRecord(
            String itemId, String itemName, double myBid, double finalPrice,
            String winnerId, String status, String endTime, String sellerName
    ) {}
}