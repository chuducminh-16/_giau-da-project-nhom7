package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.KeyEvent;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Hiển thị lịch sử đấu giá của Bidder đang đăng nhập.
 *
 * Lấy dữ liệu từ server qua message type:
 *   Client gửi:  GET_BID_HISTORY  { "bidderId": "..." }
 *   Server trả:  BID_HISTORY_RESPONSE {
 *     "success": true,
 *     "records": [
 *       {
 *         "itemId": "...",
 *         "itemName": "...",
 *         "myBid": 2500.0,        ← giá cao nhất mình đã đặt
 *         "finalPrice": 2800.0,   ← giá thắng cuộc
 *         "winnerId": "...",      ← null nếu chưa kết thúc
 *         "status": "FINISHED" | "RUNNING" | "OPEN",
 *         "endTime": "2026-12-31 23:59:59",
 *         "sellerName": "..."
 *       }, ...
 *     ]
 *   }
 */
public class BidHistoryController implements Initializable {

    // ── FXML ─────────────────────────────────────────────────────────────
    @FXML private Label  lblUsername;
    @FXML private Label  lblStatusBar;
    @FXML private Label  lblStatus;

    // Sidebar filter buttons
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterActive;
    @FXML private Button btnFilterWon;
    @FXML private Button btnFilterLost;

    // Sidebar stats
    @FXML private Label lblStatTotal;
    @FXML private Label lblStatWon;
    @FXML private Label lblStatLost;
    @FXML private Label lblStatActive;

    // Summary cards
    @FXML private Label cardTotal;
    @FXML private Label cardWon;
    @FXML private Label cardLost;
    @FXML private Label cardTotalBid;

    // Search
    @FXML private TextField txtSearch;

    // Bảng lịch sử
    @FXML private TableView<BidRecord>          historyTable;
    @FXML private TableColumn<BidRecord,String> colItemName;
    @FXML private TableColumn<BidRecord,String> colMyBid;
    @FXML private TableColumn<BidRecord,String> colFinalPrice;
    @FXML private TableColumn<BidRecord,String> colStatus;
    @FXML private TableColumn<BidRecord,String> colEndTime;
    @FXML private TableColumn<BidRecord,String> colSeller;

    // ── Data ─────────────────────────────────────────────────────────────
    // allRecords giữ toàn bộ, displayData là list đang hiển thị
    private final ObservableList<BidRecord> allRecords   = FXCollections.observableArrayList();
    private final ObservableList<BidRecord> displayData  = FXCollections.observableArrayList();

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    // ── initialize ────────────────────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Hiển thị tên user
        String username = UserSession.getInstance().getUsername();
        if (lblUsername != null && username != null)
            lblUsername.setText("👤 " + username);

        setupTable();
        client.addListener(listener);
        loadHistory();
        setStatus("Đang tải lịch sử...");
    }

    // ── Setup bảng ────────────────────────────────────────────────────────
    private void setupTable() {
        colItemName.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().itemName()));
        colMyBid.setCellValueFactory(d ->
                new SimpleStringProperty(
                        String.format("%,.0f VNĐ", d.getValue().myBid())));
        colFinalPrice.setCellValueFactory(d -> {
            double fp = d.getValue().finalPrice();
            return new SimpleStringProperty(fp > 0
                    ? String.format("%,.0f VNĐ", fp) : "Chưa kết thúc");
        });
        colEndTime.setCellValueFactory(d ->
                new SimpleStringProperty(formatDateTime(d.getValue().endTime())));
        colSeller.setCellValueFactory(d ->
                new SimpleStringProperty(d.getValue().sellerName()));

        // Cột Kết quả — màu theo trạng thái
        colStatus.setCellValueFactory(d ->
                new SimpleStringProperty(resolveResult(d.getValue())));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "🏆 Thắng"        -> "-fx-text-fill:#38a169; -fx-font-weight:bold;";
                    case "❌ Thua"         -> "-fx-text-fill:#e53e3e; -fx-font-weight:bold;";
                    case "🔴 Đang diễn ra" -> "-fx-text-fill:#4299e1; -fx-font-weight:bold;";
                    default                -> "-fx-text-fill:#718096; -fx-font-weight:bold;";
                });
            }
        });

        // Màu cột giá đặt — highlight nếu thắng
        colMyBid.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                BidRecord rec = getTableView().getItems().get(getIndex());
                setText(item);
                boolean won = isWinner(rec);
                setStyle(won
                        ? "-fx-text-fill:#38a169; -fx-font-weight:bold;"
                        : "-fx-text-fill:#2d3748;");
            }
        });

        historyTable.setItems(displayData);
    }

    // ── Load từ server ────────────────────────────────────────────────────
    private void loadHistory() {
        String bidderId = UserSession.getInstance().getUserId();
        // SỬA: Đổi tên lệnh để phân biệt rõ ràng với lịch sử của 1 sản phẩm cụ thể
        client.send(new Message("GET_USER_BID_HISTORY",
                gson.toJson(java.util.Map.of("bidderId", bidderId))));
    }

    // ── Xử lý message từ server ───────────────────────────────────────────
    private void handleServerMessage(Message msg) {
        // SỬA: Lắng nghe đúng phản hồi lịch sử cá nhân của User
        if (!"USER_BID_HISTORY_RESPONSE".equals(msg.getType())) return;

        Platform.runLater(() -> {
            try {
                String payload = msg.getPayload();
                if (payload == null || payload.isBlank()) {
                    allRecords.clear();
                    displayData.clear();
                    updateStats();
                    setStatus("Không có lịch sử đấu giá.");
                    return;
                }

                JsonArray arr = gson.fromJson(payload, JsonArray.class);
                List<BidRecord> records = new ArrayList<>();
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    records.add(new BidRecord(
                            safeStr(obj, "itemId",    ""),
                            safeStr(obj, "itemName",  "Không rõ"),
                            safeDouble(obj, "myBid"),
                            safeDouble(obj, "finalPrice"),
                            safeStr(obj, "winnerId",  ""),
                            safeStr(obj, "status",    ""),
                            safeStr(obj, "endTime",   ""),
                            safeStr(obj, "sellerName","")
                    ));
                }

                allRecords.setAll(records);
                displayData.setAll(records);
                updateStats();
                setStatus("Tải " + records.size() + " phiên thành công.");

            } catch (Exception e) {
                showError("⚠ Lỗi parse dữ liệu: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // ── Filter sidebar ────────────────────────────────────────────────────
    @FXML
    public void onFilterAll(ActionEvent event) {
        displayData.setAll(allRecords);
        setSidebarActive(btnFilterAll);
        applySearch(); // giữ từ khóa tìm kiếm nếu có
    }

    @FXML
    public void onFilterActive(ActionEvent event) {
        setSidebarActive(btnFilterActive);
        displayData.setAll(allRecords.filtered(r ->
                "RUNNING".equals(r.status()) || "OPEN".equals(r.status())));
        applySearch();
    }

    @FXML
    public void onFilterWon(ActionEvent event) {
        setSidebarActive(btnFilterWon);
        String myId = UserSession.getInstance().getUserId();
        displayData.setAll(allRecords.filtered(r -> myId.equals(r.winnerId())));
        applySearch();
    }

    @FXML
    public void onFilterLost(ActionEvent event) {
        setSidebarActive(btnFilterLost);
        String myId = UserSession.getInstance().getUserId();
        displayData.setAll(allRecords.filtered(r ->
                ("FINISHED".equals(r.status()) || "CANCELED".equals(r.status()))
                        && !myId.equals(r.winnerId())));
        applySearch();
    }

    // ── Search ────────────────────────────────────────────────────────────
    @FXML
    public void onSearch(KeyEvent event) {
        applySearch();
    }

    private void applySearch() {
        String kw = txtSearch.getText().trim().toLowerCase();

        // 1. Xác định danh sách nền dựa theo filter đang chọn ở sidebar
        ObservableList<BidRecord> baseList = FXCollections.observableArrayList();
        String myId = UserSession.getInstance().getUserId();

        if (btnFilterAll.getStyle().contains("#4299e1")) { // Đang active nút "Tất cả"
            baseList.setAll(allRecords);
        } else if (btnFilterActive.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r -> "RUNNING".equals(r.status()) || "OPEN".equals(r.status())));
        } else if (btnFilterWon.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r -> myId.equals(r.winnerId())));
        } else if (btnFilterLost.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r -> ("FINISHED".equals(r.status()) || "CANCELED".equals(r.status())) && !myId.equals(r.winnerId())));
        }

        // 2. Nếu từ khóa trống, trả về toàn bộ danh sách của filter đó
        if (kw.isEmpty()) {
            displayData.setAll(baseList);
            return;
        }

        // 3. Tiến hành lọc theo từ khóa tìm kiếm
        displayData.setAll(baseList.filtered(r ->
                r.itemName().toLowerCase().contains(kw) ||
                        r.sellerName().toLowerCase().contains(kw)));
    }

    // ── Refresh ───────────────────────────────────────────────────────────
    @FXML
    public void onRefresh(ActionEvent event) {
        txtSearch.clear();
        setSidebarActive(btnFilterAll);
        loadHistory();
        setStatus("Đang tải lại...");
    }

    // ── Back ──────────────────────────────────────────────────────────────
    @FXML
    public void onBackClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // ── Cập nhật stats ────────────────────────────────────────────────────
    private void updateStats() {
        String myId = UserSession.getInstance().getUserId();
        long total  = allRecords.size();
        long won    = allRecords.stream().filter(r -> myId.equals(r.winnerId())).count();
        long active = allRecords.stream()
                .filter(r -> "RUNNING".equals(r.status()) || "OPEN".equals(r.status())).count();
        long lost   = allRecords.stream()
                .filter(r -> ("FINISHED".equals(r.status()) || "CANCELED".equals(r.status()))
                        && !myId.equals(r.winnerId())).count();
        double totalBid = allRecords.stream().mapToDouble(BidRecord::myBid).sum();

        // Cards
        setText(cardTotal,    String.valueOf(total));
        setText(cardWon,      String.valueOf(won));
        setText(cardLost,     String.valueOf(lost));
        setText(cardTotalBid, String.format("%,.0f VNĐ", totalBid));

        // Sidebar
        setText(lblStatTotal,  "📋 Tổng: " + total);
        setText(lblStatWon,    "🏆 Đã thắng: " + won);
        setText(lblStatLost,   "❌ Đã thua: " + lost);
        setText(lblStatActive, "🔴 Đang diễn ra: " + active);
    }

    // ── Sidebar active style ──────────────────────────────────────────────
    private void setSidebarActive(Button active) {
        String off = "-fx-background-color:transparent; -fx-text-fill:#a0aec0;" +
                "-fx-alignment:CENTER_LEFT; -fx-background-radius:6;" +
                "-fx-padding:10 14; -fx-font-size:13; -fx-cursor:hand;";
        String on  = "-fx-background-color:#4299e1; -fx-text-fill:white;" +
                "-fx-alignment:CENTER_LEFT; -fx-background-radius:6;" +
                "-fx-padding:10 14; -fx-font-size:13; -fx-cursor:hand;";
        btnFilterAll.setStyle(off);
        btnFilterActive.setStyle(off);
        btnFilterWon.setStyle(off);
        btnFilterLost.setStyle(off);
        active.setStyle(on);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private boolean isWinner(BidRecord rec) {
        return UserSession.getInstance().getUserId().equals(rec.winnerId());
    }

    private String resolveResult(BidRecord rec) {
        String status = rec.status();
        if ("RUNNING".equals(status) || "OPEN".equals(status)) return "🔴 Đang diễn ra";
        if (isWinner(rec)) return "🏆 Thắng";
        return "❌ Thua";
    }

    private String formatDateTime(String raw) {
        if (raw == null || raw.isBlank()) return "--";
        try {
            String n = raw.replace("T", " ").substring(0, Math.min(16, raw.length()));
            String[] p = n.split("[\\-\\s:]");
            if (p.length >= 5) return p[2]+"/"+p[1]+"/"+p[0]+" "+p[3]+":"+p[4];
        } catch (Exception ignored) {}
        return raw;
    }

    private String safeStr(JsonObject obj, String key, String def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
        return obj.get(key).getAsString();
    }

    private double safeDouble(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return 0;
        try { return obj.get(key).getAsDouble(); } catch (Exception e) { return 0; }
    }

    private void setText(Label lbl, String text) {
        if (lbl != null) lbl.setText(text);
    }

    private void setStatus(String msg) {
        if (lblStatusBar != null) lblStatusBar.setText(msg);
    }

    private void showError(String msg) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle("-fx-text-fill:#e53e3e; -fx-font-size:12px;");
        lblStatus.setVisible(true);
        lblStatus.setManaged(true);
    }

    // ── Record dữ liệu 1 phiên ────────────────────────────────────────────
    public record BidRecord(
            String itemId,
            String itemName,
            double myBid,        // giá cao nhất mình đặt
            double finalPrice,   // giá thắng cuộc (0 nếu chưa kết thúc)
            String winnerId,     // null/"" nếu chưa kết thúc
            String status,       // OPEN | RUNNING | FINISHED | CANCELED
            String endTime,
            String sellerName
    ) {}
}

