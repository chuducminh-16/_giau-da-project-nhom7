package com.auction.client.controller;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.BidItem;
import com.auction.client.SceneEngine;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.auction.client.utils.ToastNotification;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.Stage;
import javafx.util.Duration;


public class LiveBiddingController implements Initializable {

    @FXML private Label     lblCountdown;
    @FXML private Label     lblProductName;
    @FXML private Label     lblCurrentBid;
    @FXML private TextField txtBidAmount;
    @FXML private LineChart<String, Number> priceChart;

    @FXML private TableView<BidItem>          tableBidHistory;
    @FXML private TableColumn<BidItem,String> colTime;
    @FXML private TableColumn<BidItem,String> colUser;
    @FXML private TableColumn<BidItem,String> colAmount;

    @FXML private ListView<String> listNotifications;

    private Item     currentItem;
    private String   itemId;
    private XYChart.Series<String, Number> priceSeries;
    private Timeline countdownTimeline;
    private long     secondsRemaining;

    // Giá cao nhất hiện tại — cập nhật cả từ lịch sử lẫn BID_UPDATE realtime
    private double latestBid = 0;

    // true sau khi BID_HISTORY_RESPONSE đến — ngăn PRODUCT_DETAIL ghi đè giá
    private boolean historyLoaded = false;

    private final ObservableList<BidItem> bidHistory    = FXCollections.observableArrayList();
    private final ObservableList<String>  notifications = FXCollections.observableArrayList();

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupChart();
        client.addListener(listener);

        itemId = SelectedProductSession.getInstance().getProductId();

        if (itemId != null) {
            addLog("Đang kết nối phòng đấu giá...");
            client.send(new Message("GET_PRODUCT_DETAIL",
                    gson.toJson(Map.of("itemId", itemId))));
            // Server AuctionController.handleGetBidHistory nhận key "productId"
            client.send(new Message("GET_BID_HISTORY",
                    gson.toJson(Map.of("productId", itemId))));
        } else {
            addLog("Không tìm thấy thông tin sản phẩm!");
        }
    }

    private void handleServerMessage(Message msg) {
        switch (msg.getType()) {

            case "PRODUCT_DETAIL_RESPONSE" -> {
                try {
                    JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                    if (!root.has("success") || !root.get("success").getAsBoolean()) {
                        Platform.runLater(() -> addLog("Không tìm thấy sản phẩm!"));
                        return;
                    }
                    Item item = gson.fromJson(root.get("item"), Item.class);
                    if (item == null) return;

                    Platform.runLater(() -> {
                        currentItem = item;
                        itemId = item.getId();
                        if (lblProductName != null) lblProductName.setText(item.getName());

                        // Chỉ dùng giá từ PRODUCT_DETAIL nếu lịch sử chưa đến
                        if (!historyLoaded) {
                            latestBid = item.getCurrentBid();
                            updateCurrentBidLabel(latestBid);
                            addChartPoint(formatTime(null), latestBid);
                        }

                        startCountdownFromItem();
                        client.send(new Message("WATCH_AUCTION",
                                gson.toJson(Map.of("auctionId", item.getId()))));
                        addLog("Chào mừng vào phòng đấu giá: " + item.getName());
                    });
                } catch (Exception e) {
                    System.err.println("[LiveBidding] PRODUCT_DETAIL_RESPONSE lỗi: " + e.getMessage());
                }
            }

            // ─────────────────────────────────────────────────────────────
            // BID_HISTORY_RESPONSE
            //
            // Server trả JsonArray trực tiếp (List<BidRecord> được Gson serialize):
            //   [{"bidderId":"...","username":"...","amount":101000.0,"createdAt":"2026-05-24 10:37:10"}, ...]
            //
            // Thứ tự: ORDER BY bid_time DESC → index 0 = mới nhất
            //
            // FIX TIMEZONE:
            //   - DB Railway chạy UTC, connection string có serverTimezone=Asia/Ho_Chi_Minh
            //   - getString("bid_time") trả chuỗi theo giờ server DB (UTC)
            //   - Cần cộng +7 để ra giờ VN
            //   - BID_UPDATE realtime dùng LocalTime.now() của máy client → đã đúng giờ VN
            //     → KHÔNG cộng +7 cho BID_UPDATE
            // ─────────────────────────────────────────────────────────────
            case "BID_HISTORY_RESPONSE" -> {
                try {
                    String payload = msg.getPayload();
                    if (payload == null || payload.isBlank()) return;

                    // Parse payload: có thể là JsonArray trực tiếp hoặc JsonObject bọc ngoài
                    JsonArray arr = null;
                    try {
                        arr = gson.fromJson(payload, JsonArray.class);
                    } catch (Exception e1) {
                        try {
                            JsonObject root = gson.fromJson(payload, JsonObject.class);
                            if (root.has("history") && root.get("history").isJsonArray())
                                arr = root.getAsJsonArray("history");
                            else if (root.has("data") && root.get("data").isJsonArray())
                                arr = root.getAsJsonArray("data");
                        } catch (Exception e2) {
                            System.err.println("[LiveBidding] Không parse được BID_HISTORY_RESPONSE");
                            return;
                        }
                    }

                    if (arr == null || arr.size() == 0) return;

                    // Dùng record để giữ time + price + user cùng nhau — tránh index mismatch
                    record HistoryEntry(String timeLabel, String username,
                                        String priceStr, double price) {}

                    List<HistoryEntry> entries = new ArrayList<>();
                    double maxBid = 0;

                    for (JsonElement el : arr) {
                        JsonObject row = el.getAsJsonObject();

                        // Key "createdAt" từ BidRecord (doc 71), fallback "bidTime" từ Map cũ
                        String rawTime = safeStr(row, "createdAt",
                                         safeStr(row, "bidTime", ""));

                        String username = safeStr(row, "username", "?");

                        // Key "amount" từ BidRecord (doc 71), fallback "bidPrice" từ Map cũ
                        double price = safeDouble(row, "amount") > 0
                                       ? safeDouble(row, "amount")
                                       : safeDouble(row, "bidPrice");

                        // FIX TIMEZONE: DB lưu UTC → cộng +7 để ra giờ VN
                        // (chỉ áp dụng cho lịch sử DB, KHÔNG áp dụng cho BID_UPDATE realtime)
                        String displayTime = convertUtcToVnTime(rawTime);

                        entries.add(new HistoryEntry(displayTime, username,
                                String.format("%,.0f VNĐ", price), price));

                        if (price > maxBid) maxBid = price;
                    }

                    final double finalMaxBid = maxBid;
                    final List<HistoryEntry> finalEntries = entries;

                    Platform.runLater(() -> {
                        historyLoaded = true;

                        // Điền bảng: arr ORDER BY bid_time DESC → entries[0] = mới nhất → đặt ở đầu bảng ✓
                        List<BidItem> rows = new ArrayList<>();
                        for (HistoryEntry e : finalEntries) {
                            rows.add(new BidItem(e.timeLabel(), e.username(), e.priceStr()));
                        }
                        bidHistory.setAll(rows);

                        // Cập nhật giá hiện tại theo giá cao nhất trong lịch sử
                        if (finalMaxBid > 0) {
                            latestBid = finalMaxBid;
                            updateCurrentBidLabel(latestBid);
                            if (currentItem != null) currentItem.setCurrentBid(latestBid);
                        }

                        // Vẽ chart theo thứ tự CŨ → MỚI:
                        // entries[0]=mới nhất → loop từ cuối về đầu (size-1 → 0)
                        // Mỗi entry giữ cả timeLabel lẫn price → luôn khớp nhau
                        priceSeries.getData().clear();
                        for (int i = finalEntries.size() - 1; i >= 0; i--) {
                            HistoryEntry e = finalEntries.get(i);
                            addChartPoint(e.timeLabel(), e.price());
                        }

                        if (!finalEntries.isEmpty()) {
                            addLog("Đã tải " + finalEntries.size() + " lần đặt giá trước đó.");
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[LiveBidding] BID_HISTORY_RESPONSE lỗi: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // BID_UPDATE: nhận từ server khi có người đặt giá mới
            // Thời gian dùng LocalTime.now() của máy client → đã đúng giờ VN → KHÔNG cộng +7
            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();
                    if (itemId == null || !itemId.equals(productId)) return;

                    double newBid     = dto.get("newBid").getAsDouble();
                    String bidderName = dto.has("bidderName")
                            ? dto.get("bidderName").getAsString() : "Unknown";

                    Platform.runLater(() -> {
                        latestBid = newBid;
                        if (currentItem != null) currentItem.setCurrentBid(newBid);
                        updateCurrentBidLabel(newBid);

                        // Dùng LocalTime.now() — giờ local của máy client, đã đúng VN, KHÔNG +7
                        String time = LocalTime.now().format(timeFmt);
                        bidHistory.add(0, new BidItem(time, bidderName,
                                String.format("%,.0f VNĐ", newBid)));
                        addChartPoint(time, newBid);
                        addLog(String.format("%s vừa đặt %,.0f VNĐ", bidderName, newBid));

                        // Toast bid mới — hiển cho tất cả mọi người
                        try {
                            Stage stage = (Stage) lblCountdown.getScene().getWindow();
                            String itemName = currentItem != null ? currentItem.getName() : "";
                            ToastNotification.bid(stage, bidderName, itemName, newBid);

                        } catch (Exception ex) {
                            System.err.println("[LiveBidding] Toast loi: " + ex.getMessage());
                        }
                        
                    });
                } catch (Exception e) {
                    System.err.println("[LiveBidding] BID_UPDATE lỗi: " + e.getMessage());
                }
            }

            case "BID_RESULT" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    boolean success = dto.get("success").getAsBoolean();
                    String  message = dto.has("message") ? dto.get("message").getAsString() : "";
                    Platform.runLater(() ->
                            addLog(success ? "✅ Đặt giá thành công!" : "❌ Thất bại: " + message));
                } catch (Exception e) {
                    System.err.println("[LiveBidding] BID_RESULT lỗi: " + e.getMessage());
                }
            }

            case "TIME_EXTENDED", "AUCTION_EXTENDED" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String newEndTimeStr = dto.has("newEndTime")
                            ? dto.get("newEndTime").getAsString() : null;
                    if (newEndTimeStr == null) return;

                    LocalDateTime newEnd = LocalDateTime.parse(newEndTimeStr.replace(" ", "T"));
                    long newSeconds = java.time.Duration
                            .between(LocalDateTime.now(), newEnd).getSeconds();
                    if (newSeconds <= 0) return;

                    Platform.runLater(() -> {
                        if (currentItem != null) currentItem.setEndTime(newEnd);
                        secondsRemaining = newSeconds;
                        if (countdownTimeline != null) countdownTimeline.stop();
                        startCountdownTimer();
                        addLog("⏰ Phiên được gia hạn thêm 60 giây!");
                    });
                } catch (Exception e) {
                    System.err.println("[LiveBidding] TIME_EXTENDED lỗi: " + e.getMessage());
                }
            }

            case "AUCTION_ENDED" -> {
                Platform.runLater(() -> {
                    if (countdownTimeline != null) countdownTimeline.stop();
                    onAuctionEnded();
                    try {
                        JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                        String winnerName = dto.has("winnerName") && !dto.get("winnerName").isJsonNull()
                                ? dto.get("winnerName").getAsString() : null;
                        double finalPrice = dto.has("finalPrice")
                                ? dto.get("finalPrice").getAsDouble() : 0;
                        String message = dto.has("message") && !dto.get("message").isJsonNull()
                                ? dto.get("message").getAsString() : null;

                        if (message != null) addLog(message);
                        else if (winnerName != null && finalPrice > 0)
                            addLog(String.format("🏆 Người thắng: %s — Giá: %,.0f VNĐ",
                                    winnerName, finalPrice));
                        else addLog("Phiên đấu giá đã kết thúc — không có người đặt giá.");

                        // Toast thắng/thua
                        Stage stage = (Stage) lblCountdown.getScene().getWindow();
                        String myName = UserSession.getInstance().getUsername();
                        String itemName = currentItem != null ? currentItem.getName() : "";
                        if (winnerName != null && winnerName.equals(myName))
                            ToastNotification.win(stage, itemName, finalPrice);
                        else if (finalPrice > 0)
                            ToastNotification.lose(stage, itemName);
                    } catch (Exception e) {
                        addLog("Phiên đấu giá đã kết thúc!");
                    }
                });
            }
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────

    private void updateCurrentBidLabel(double bid) {
        if (lblCurrentBid != null)
            lblCurrentBid.setText(String.format("Current Bid: %,.0f VNĐ", bid));
    }

    private void startCountdownFromItem() {
        if (currentItem == null) return;
        secondsRemaining = currentItem.getSecondsRemaining();
        if (secondsRemaining <= 0) {
            if (lblCountdown != null) {
                lblCountdown.setText("ĐÃ KẾT THÚC");
                lblCountdown.setStyle("-fx-text-fill: #a0aec0;");
            }
            if (txtBidAmount != null) txtBidAmount.setDisable(true);
            return;
        }
        startCountdownTimer();
    }

    private void startCountdownTimer() {
        if (countdownTimeline != null) countdownTimeline.stop();
        countdownTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    secondsRemaining--;
                    updateCountdownLabel();
                    if (secondsRemaining <= 0) {
                        countdownTimeline.stop();
                        onAuctionEnded();
                    }
                })
        );
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.playFromStart();
        updateCountdownLabel();
    }

    private void updateCountdownLabel() {
        if (lblCountdown == null) return;
        long h = secondsRemaining / 3600;
        long m = (secondsRemaining % 3600) / 60;
        long s = secondsRemaining % 60;
        lblCountdown.setText(h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s));
        if (secondsRemaining <= 60)
            lblCountdown.setStyle("-fx-text-fill:#fc8181; -fx-font-size:22; -fx-font-weight:bold;");
    }

    private void onAuctionEnded() {
        if (lblCountdown != null) {
            lblCountdown.setText("KẾT THÚC!");
            lblCountdown.setStyle("-fx-text-fill:#a0aec0;");
        }
        if (txtBidAmount != null) txtBidAmount.setDisable(true);
    }

    // ── FXML Handlers ─────────────────────────────────────────────────────

    @FXML
    public void onPlaceBidClick(ActionEvent event) {
        if (currentItem == null) {
            addLog("Đang tải thông tin sản phẩm, vui lòng chờ...");
            return;
        }
        if (secondsRemaining <= 0) {
            addLog("Phiên đấu giá đã kết thúc.");
            return;
        }

        String input = txtBidAmount.getText().trim();
        if (input.isEmpty()) return;

        double newAmount;
        try {
            newAmount = Double.parseDouble(input.replaceAll(",", ""));
        } catch (NumberFormatException e) {
            addLog("Lỗi: Vui lòng nhập số tiền hợp lệ!");
            return;
        }

        double effectiveBid = Math.max(latestBid, currentItem.getCurrentBid());
        double minBid       = effectiveBid + currentItem.getBidIncrement();

        if (newAmount < minBid) {
            addLog(String.format("Giá phải ít nhất %,.0f VNĐ (tăng thêm %,.0f VNĐ)",
                    minBid, currentItem.getBidIncrement()));
            return;
        }

        client.send(new Message("PLACE_BID", gson.toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId",  UserSession.getInstance().getUserId(),
                "amount",    newAmount
        ))));
        txtBidAmount.clear();
        addLog(String.format("Đã gửi mức giá: %,.0f VNĐ — Đang chờ xác nhận...", newAmount));
    }

    @FXML
    public void onLeaveRoomClick(ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        client.removeListener(listener);
        SceneEngine.changeScene(event, "detail-view.fxml", "Chi tiết sản phẩm");
    }

    // ── Setup ─────────────────────────────────────────────────────────────

    private void setupTable() {
        colTime.setCellValueFactory(c -> c.getValue().timeProperty());
        colUser.setCellValueFactory(c -> c.getValue().userProperty());
        colAmount.setCellValueFactory(c -> c.getValue().amountProperty());
        tableBidHistory.setItems(bidHistory);
        if (listNotifications != null) listNotifications.setItems(notifications);
    }

    private void setupChart() {
        priceSeries = new XYChart.Series<>();
        priceSeries.setName("Giá đấu cao nhất");
        if (priceChart != null) {
            priceChart.getData().clear();
            priceChart.getData().add(priceSeries);
            priceChart.setAnimated(false);
            priceChart.setTitle("Diễn biến giá theo thời gian thực");
            priceChart.getXAxis().setLabel("Thời gian");
            priceChart.getYAxis().setLabel("Giá (VNĐ)");
            NumberAxis yAxis = (NumberAxis) priceChart.getYAxis();
            yAxis.setForceZeroInRange(false);
            yAxis.setAutoRanging(true);
            priceChart.setCreateSymbols(true);
        }
    }

    private void addChartPoint(String timeLabel, double price) {
        if (priceChart == null || priceSeries == null) return;
        XYChart.Data<String, Number> dataPoint = new XYChart.Data<>(timeLabel, price);
        priceSeries.getData().add(dataPoint);
        Platform.runLater(() -> {
            if (dataPoint.getNode() != null) {
                Tooltip tip = new Tooltip(
                        String.format("⏱ %s%n💰 %,.0f VNĐ", timeLabel, price));
                tip.setStyle("-fx-background-color:#1a1f2e; -fx-text-fill:white;" +
                        "-fx-font-size:12px; -fx-padding:6 10;");
                Tooltip.install(dataPoint.getNode(), tip);
            }
        });
        if (priceSeries.getData().size() > 30)
            priceSeries.getData().remove(0);
    }

    private void addLog(String message) {
        String time = LocalTime.now().format(timeFmt);
        Platform.runLater(() -> notifications.add(0, "[" + time + "] " + message));
    }

    private String formatTime(String fallback) {
        return fallback != null ? fallback : LocalTime.now().format(timeFmt);
    }

    /**
     * Convert thời gian từ DB (UTC) sang giờ Việt Nam (UTC+7).
     *
     * DB Railway chạy UTC. getString("bid_time") trả UTC string.
     * serverTimezone=Asia/Ho_Chi_Minh trong connection string chỉ ảnh hưởng
     * getTimestamp(), KHÔNG ảnh hưởng getString() → phải convert thủ công.
     *
     * QUAN TRỌNG: Chỉ gọi hàm này cho lịch sử từ DB (BID_HISTORY_RESPONSE).
     * KHÔNG gọi cho BID_UPDATE realtime (đã dùng LocalTime.now() của máy client).
     *
     * Input:  "2026-05-24 10:37:10" hoặc "2026-05-24T10:37:10"
     * Output: "17:37:10"
     */
    private String convertUtcToVnTime(String raw) {
        if (raw == null || raw.isBlank()) return "--:--:--";
        try {
            String normalized = raw.replace("T", " ").trim();
            String[] parts = normalized.split(" ");
            if (parts.length < 2) return raw;

            String[] timeParts = parts[1].split(":");
            if (timeParts.length < 2) return parts[1];

            int hour   = Integer.parseInt(timeParts[0].trim());
            int minute = Integer.parseInt(timeParts[1].trim());
            int second = timeParts.length >= 3
                    ? Integer.parseInt(timeParts[2].trim().substring(0, Math.min(2, timeParts[2].trim().length())))
                    : 0;

            // UTC+7 (Asia/Ho_Chi_Minh)
            hour = (hour + 7) % 24;

            return String.format("%02d:%02d:%02d", hour, minute, second);
        } catch (Exception e) {
            // Fallback: lấy 8 ký tự cuối (phần giờ:phút:giây)
            return raw.length() >= 8 ? raw.substring(raw.length() - 8) : raw;
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────

    private String safeStr(JsonObject obj, String key, String def) {
        try {
            return obj.has(key) && !obj.get(key).isJsonNull()
                    ? obj.get(key).getAsString() : def;
        } catch (Exception e) { return def; }
    }

    private double safeDouble(JsonObject obj, String key) {
        try { return obj.has(key) ? obj.get(key).getAsDouble() : 0; }
        catch (Exception e) { return 0; }
    }
}
