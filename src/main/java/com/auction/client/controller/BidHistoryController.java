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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class BidHistoryController implements Initializable {

    @FXML private Label  lblUsername;
    @FXML private Label  lblStatusBar;
    @FXML private Label  lblStatus;

    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterActive;
    @FXML private Button btnFilterWon;
    @FXML private Button btnFilterLost;

    @FXML private Label lblStatTotal;
    @FXML private Label lblStatWon;
    @FXML private Label lblStatLost;
    @FXML private Label lblStatActive;

    @FXML private Label cardTotal;
    @FXML private Label cardWon;
    @FXML private Label cardLost;
    @FXML private Label cardTotalBid;

    @FXML private TextField txtSearch;

    @FXML private TableView<BidRecord>          historyTable;
    @FXML private TableColumn<BidRecord,String> colItemName;
    @FXML private TableColumn<BidRecord,String> colMyBid;
    @FXML private TableColumn<BidRecord,String> colFinalPrice;
    @FXML private TableColumn<BidRecord,String> colStatus;
    @FXML private TableColumn<BidRecord,String> colEndTime;
    @FXML private TableColumn<BidRecord,String> colSeller;

    private final ObservableList<BidRecord> allRecords  = FXCollections.observableArrayList();
    private final ObservableList<BidRecord> displayData = FXCollections.observableArrayList();

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String username = UserSession.getInstance().getUsername();
        if (lblUsername != null && username != null)
            lblUsername.setText("👤 " + username);

        setupTable();
        client.addListener(listener);
        loadHistory();
        setStatus("Đang tải lịch sử...");
    }

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

        // ── FIX: resolveResult() kiểm tra endTime để không hiện "Đang diễn ra"
        // khi phiên thực ra đã hết giờ (DB chưa cập nhật status kịp)
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

    private void loadHistory() {
        String bidderId = UserSession.getInstance().getUserId();
        client.send(new Message("GET_USER_BID_HISTORY",
                gson.toJson(java.util.Map.of("bidderId", bidderId))));
    }

    private void handleServerMessage(Message msg) {
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
        applySearch();
    }

    @FXML
    public void onFilterActive(ActionEvent event) {
        setSidebarActive(btnFilterActive);
        // FIX: "Đang diễn ra" = status RUNNING/OPEN VÀ endTime chưa qua
        displayData.setAll(allRecords.filtered(r ->
                ("RUNNING".equals(r.status()) || "OPEN".equals(r.status()))
                && !isExpired(r.endTime())));
        applySearch();
    }

    @FXML
    public void onFilterWon(ActionEvent event) {
        setSidebarActive(btnFilterWon);
        String myId = UserSession.getInstance().getUserId();
        // FIX: thắng = winnerId khớp VÀ phiên đã thực sự kết thúc
        displayData.setAll(allRecords.filtered(r ->
                myId.equals(r.winnerId())
                && (isFinishedStatus(r.status()) || isExpired(r.endTime()))));
        applySearch();
    }

    @FXML
    public void onFilterLost(ActionEvent event) {
        setSidebarActive(btnFilterLost);
        String myId = UserSession.getInstance().getUserId();
        // FIX: thua = phiên đã kết thúc (status hoặc endTime) VÀ không phải winner
        displayData.setAll(allRecords.filtered(r ->
                (isFinishedStatus(r.status()) || isExpired(r.endTime()))
                && !myId.equals(r.winnerId())));
        applySearch();
    }

    @FXML
    public void onSearch(KeyEvent event) {
        applySearch();
    }

    private void applySearch() {
        String kw = txtSearch.getText().trim().toLowerCase();
        String myId = UserSession.getInstance().getUserId();

        ObservableList<BidRecord> baseList = FXCollections.observableArrayList();

        if (btnFilterAll.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords);
        } else if (btnFilterActive.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r ->
                    ("RUNNING".equals(r.status()) || "OPEN".equals(r.status()))
                    && !isExpired(r.endTime())));
        } else if (btnFilterWon.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r ->
                    myId.equals(r.winnerId())
                    && (isFinishedStatus(r.status()) || isExpired(r.endTime()))));
        } else if (btnFilterLost.getStyle().contains("#4299e1")) {
            baseList.setAll(allRecords.filtered(r ->
                    (isFinishedStatus(r.status()) || isExpired(r.endTime()))
                    && !myId.equals(r.winnerId())));
        }

        if (kw.isEmpty()) {
            displayData.setAll(baseList);
            return;
        }

        displayData.setAll(baseList.filtered(r ->
                r.itemName().toLowerCase().contains(kw) ||
                r.sellerName().toLowerCase().contains(kw)));
    }

    @FXML
    public void onRefresh(ActionEvent event) {
        txtSearch.clear();
        setSidebarActive(btnFilterAll);
        loadHistory();
        setStatus("Đang tải lại...");
    }

    @FXML
    public void onBackClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // ── Stats ─────────────────────────────────────────────────────────────

    private void updateStats() {
        String myId = UserSession.getInstance().getUserId();
        long total  = allRecords.size();

        // FIX: active = status RUNNING/OPEN VÀ endTime chưa qua
        long active = allRecords.stream()
                .filter(r -> ("RUNNING".equals(r.status()) || "OPEN".equals(r.status()))
                          && !isExpired(r.endTime()))
                .count();

        // FIX: won/lost = phiên đã thực sự kết thúc
        long won = allRecords.stream()
                .filter(r -> myId.equals(r.winnerId())
                          && (isFinishedStatus(r.status()) || isExpired(r.endTime())))
                .count();

        long lost = allRecords.stream()
                .filter(r -> (isFinishedStatus(r.status()) || isExpired(r.endTime()))
                          && !myId.equals(r.winnerId()))
                .count();

        double totalBid = allRecords.stream().mapToDouble(BidRecord::myBid).sum();

        setText(cardTotal,    String.valueOf(total));
        setText(cardWon,      String.valueOf(won));
        setText(cardLost,     String.valueOf(lost));
        setText(cardTotalBid, String.format("%,.0f VNĐ", totalBid));

        setText(lblStatTotal,  "📋 Tổng: " + total);
        setText(lblStatWon,    "🏆 Đã thắng: " + won);
        setText(lblStatLost,   "❌ Đã thua: " + lost);
        setText(lblStatActive, "🔴 Đang diễn ra: " + active);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private boolean isWinner(BidRecord rec) {
        return UserSession.getInstance().getUserId().equals(rec.winnerId());
    }

    /** Phiên có status kết thúc trong DB. */
    private boolean isFinishedStatus(String status) {
        return "FINISHED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status);
    }

    /**
     * FIX CHÍNH: Kiểm tra endTime đã qua chưa.
     * Dùng để xử lý trường hợp DB chưa kịp cập nhật status
     * (scheduler chạy mỗi 10s, có thể delay).
     */
    private boolean isExpired(String endTimeStr) {
        if (endTimeStr == null || endTimeStr.isBlank()) return false;
        try {
            String normalized = endTimeStr.replace(" ", "T");
            // Đảm bảo không bị lỗi nếu chuỗi có phần giây lẻ
            if (normalized.length() > 19) normalized = normalized.substring(0, 19);
            LocalDateTime endTime = LocalDateTime.parse(normalized);
            return LocalDateTime.now().isAfter(endTime);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * FIX CHÍNH: Xác định kết quả hiển thị.
     * Ưu tiên kiểm tra endTime thực tế trước, không tin hoàn toàn vào status DB.
     */
    private String resolveResult(BidRecord rec) {
        String status = rec.status();
        boolean expired = isExpired(rec.endTime());

        // Nếu status là RUNNING/OPEN nhưng thực tế đã hết giờ
        if (("RUNNING".equals(status) || "OPEN".equals(status)) && expired) {
            return isWinner(rec) ? "🏆 Thắng" : "❌ Thua";
        }

        // Đang thực sự diễn ra (chưa hết giờ)
        if (("RUNNING".equals(status) || "OPEN".equals(status)) && !expired) {
            return "🔴 Đang diễn ra";
        }

        // Status đã FINISHED/CANCELED/PAID
        return isWinner(rec) ? "🏆 Thắng" : "❌ Thua";
    }

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

    public record BidRecord(
            String itemId,
            String itemName,
            double myBid,
            double finalPrice,
            String winnerId,
            String status,
            String endTime,
            String sellerName
    ) {}
}
