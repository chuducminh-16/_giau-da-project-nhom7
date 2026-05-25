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
import com.auction.client.handler.AuctionEndedHandler;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;


public class LiveBiddingController implements Initializable {

    // ── FXML (giữ nguyên fx:id cũ) ───────────────────────────────────────
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

    // ── FXML mới: Auto-Bid ────────────────────────────────────────────────
    @FXML private TextField txtMaxBid;
    @FXML private Button    btnAutoBid;
    @FXML private Label     lblAutoBidStatus;
    @FXML private Label     lblAutoBidInfo;
    @FXML private HBox      hboxAutoBidInfo;

    // ── Auto-bid state ────────────────────────────────────────────────────
    private boolean autoBidActive   = false;
    private double  autoBidMaxPrice = 0;

    // ── State cũ (giữ nguyên) ─────────────────────────────────────────────
    private Item     currentItem;
    private String   itemId;
    private XYChart.Series<String, Number> priceSeries;
    private Timeline countdownTimeline;
    private long     secondsRemaining;
    private double   latestBid     = 0;
    private boolean  historyLoaded = false;

    private final ObservableList<BidItem> bidHistory    = FXCollections.observableArrayList();
    private final ObservableList<String>  notifications = FXCollections.observableArrayList();

    private final Gson          gson    = buildGson();
    private final NetworkClient client  = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    // ── AuctionEndedHandler — khởi tạo sau initialize() vì cần scene ─────
    // Dùng lazy getter thay vì khởi tạo ngay để tránh Stage = null
    private AuctionEndedHandler endedHandler;

    // ── Gson builder (giữ nguyên) ─────────────────────────────────────────
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

    // ── Initialize ────────────────────────────────────────────────────────
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
            client.send(new Message("GET_BID_HISTORY",
                    gson.toJson(Map.of("productId", itemId))));
        } else {
            addLog("Không tìm thấy thông tin sản phẩm!");
        }

        // Khởi tạo AuctionEndedHandler với lazy Stage supplier
        // (Stage chỉ có sau khi scene đã attach vào window)
        endedHandler = new AuctionEndedHandler(
                gson,
                this::getStage,       // lazy — tránh NullPointerException khi initialize
                () -> currentItem,
                this::onAuctionEnded,
                this::addLog
        );

        // Tooltip cho nút auto-bid
        if (btnAutoBid != null) {
            Tooltip tip = new Tooltip(
                    "Nhập giá tối đa rồi nhấn Kích hoạt.\n" +
                    "Khi có người trả cao hơn, hệ thống tự đặt = giá hiện tại + bước nhảy,\n" +
                    "cho đến khi đạt giá tối đa bạn đã đặt."
            );
            tip.setStyle("-fx-background-color:#1a1f2e; -fx-text-fill:white;" +
                    "-fx-font-size:12px; -fx-padding:8 12;");
            Tooltip.install(btnAutoBid, tip);
        }
    }

    // ── Lazy Stage getter ─────────────────────────────────────────────────
    private Stage getStage() {
        try {
            if (lblCountdown != null && lblCountdown.getScene() != null)
                return (Stage) lblCountdown.getScene().getWindow();
            if (lblCurrentBid != null && lblCurrentBid.getScene() != null)
                return (Stage) lblCurrentBid.getScene().getWindow();
        } catch (Exception e) {
            System.err.println("[LiveBidding] getStage() lỗi: " + e.getMessage());
        }
        return null;
    }

    // ═════════════════════════════════════════════════════════════════════
    //  AUTO-BID (giữ nguyên hoàn toàn)
    // ═════════════════════════════════════════════════════════════════════

    @FXML
    public void onAutoBidToggle(ActionEvent event) {
        if (autoBidActive) {
            deactivateAutoBid();
            addLog("⚡ Auto-bid đã tắt.");
        } else {
            if (!validateAndActivateAutoBid()) return;
            addLog(String.format("⚡ Auto-bid đã BẬT — tối đa %,.0f VNĐ", autoBidMaxPrice));
        }
    }

    private boolean validateAndActivateAutoBid() {
        String raw = txtMaxBid.getText().trim().replaceAll(",", "");
        if (raw.isEmpty()) {
            addLog("❌ Vui lòng nhập giá tối đa trước khi kích hoạt Auto-bid.");
            flashInputError(txtMaxBid);
            return false;
        }

        double maxPrice;
        try {
            maxPrice = Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            addLog("❌ Giá tối đa không hợp lệ. Vui lòng nhập số.");
            flashInputError(txtMaxBid);
            return false;
        }

        if (maxPrice <= 0) {
            addLog("❌ Giá tối đa phải lớn hơn 0.");
            flashInputError(txtMaxBid);
            return false;
        }

        double currentBid = Math.max(latestBid,
                currentItem != null ? currentItem.getCurrentBid() : 0);

        if (maxPrice <= currentBid) {
            addLog(String.format(
                    "❌ Giá tối đa (%.0f) phải cao hơn giá hiện tại (%.0f).",
                    maxPrice, currentBid));
            flashInputError(txtMaxBid);
            return false;
        }

        autoBidMaxPrice = maxPrice;
        autoBidActive   = true;

        txtMaxBid.setDisable(true);
        btnAutoBid.setText("⏹ Dừng Auto-bid");
        btnAutoBid.setStyle(
                "-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                "-fx-font-size: 13; -fx-font-weight: bold;" +
                "-fx-background-radius: 6; -fx-cursor: hand;");

        setAutoBidStatusBadge(true);

        if (hboxAutoBidInfo != null) {
            hboxAutoBidInfo.setVisible(true);
            hboxAutoBidInfo.setManaged(true);
        }
        if (lblAutoBidInfo != null) {
            lblAutoBidInfo.setText(String.format(
                    "Tối đa: %,.0f VNĐ  —  Đang chờ bid đối thủ...", autoBidMaxPrice));
        }
        return true;
    }

    private void deactivateAutoBid() {
        autoBidActive   = false;
        autoBidMaxPrice = 0;

        txtMaxBid.setDisable(false);
        btnAutoBid.setText("⚡ Kích hoạt");
        btnAutoBid.setStyle(
                "-fx-background-color: #d69e2e; -fx-text-fill: white;" +
                "-fx-font-size: 13; -fx-font-weight: bold;" +
                "-fx-background-radius: 6; -fx-cursor: hand;");

        setAutoBidStatusBadge(false);

        if (hboxAutoBidInfo != null) {
            hboxAutoBidInfo.setVisible(false);
            hboxAutoBidInfo.setManaged(false);
        }
    }

    private void setAutoBidStatusBadge(boolean active) {
        if (lblAutoBidStatus == null) return;
        if (active) {
            lblAutoBidStatus.setText("● BẬT");
            lblAutoBidStatus.setStyle(
                    "-fx-text-fill: #68d391; -fx-font-size: 11; -fx-font-weight: bold;" +
                    "-fx-background-color: #1c4532; -fx-background-radius: 20;" +
                    "-fx-padding: 3 10;");
        } else {
            lblAutoBidStatus.setText("● TẮT");
            lblAutoBidStatus.setStyle(
                    "-fx-text-fill: #718096; -fx-font-size: 11; -fx-font-weight: bold;" +
                    "-fx-background-color: #2d3748; -fx-background-radius: 20;" +
                    "-fx-padding: 3 10;");
        }
    }

    private void flashInputError(TextField field) {
        String original = field.getStyle();
        field.setStyle(original + "; -fx-border-color: #e53e3e; -fx-border-width: 2;");
        Timeline reset = new Timeline(
                new KeyFrame(Duration.seconds(1.5), e -> field.setStyle(original)));
        reset.setCycleCount(1);
        reset.play();
    }

    private void handleAutoBidIfNeeded(String lastBidderName) {
        if (!autoBidActive) return;
        if (currentItem == null) return;
        if (secondsRemaining <= 0) return;

        String myUsername = UserSession.getInstance().getUsername();

        if (myUsername != null && myUsername.equals(lastBidderName)) {
            if (lblAutoBidInfo != null) {
                lblAutoBidInfo.setText(String.format(
                        "Tối đa: %,.0f VNĐ  —  Bạn đang dẫn đầu!", autoBidMaxPrice));
            }
            return;
        }

        double increment  = currentItem.getBidIncrement() > 0 ? currentItem.getBidIncrement() : 1000;
        double nextBidAmt = latestBid + increment;

        if (nextBidAmt > autoBidMaxPrice) {
            addLog(String.format(
                    "⚡ Auto-bid dừng: giá tiếp theo (%.0f) vượt giá tối đa (%.0f).",
                    nextBidAmt, autoBidMaxPrice));
            deactivateAutoBid();
            try {
                Stage stage = getStage();
                if (stage != null)
                    ToastNotification.warn(stage,
                            "⚡ Auto-bid đã dừng",
                            String.format("Giá vượt mức tối đa %.0f VNĐ bạn đã đặt.",
                                    autoBidMaxPrice));
            } catch (Exception ignored) {}
            return;
        }

        addLog(String.format("🤖 Auto-bid: đang đặt %,.0f VNĐ...", nextBidAmt));
        if (lblAutoBidInfo != null) {
            lblAutoBidInfo.setText(String.format(
                    "Tối đa: %,.0f VNĐ  —  Vừa đặt tự động %,.0f VNĐ",
                    autoBidMaxPrice, nextBidAmt));
        }

        final double bidToSend = nextBidAmt;
        client.send(new Message("PLACE_BID", gson.toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId",  UserSession.getInstance().getUserId(),
                "amount",    bidToSend
        ))));
    }

    // ═════════════════════════════════════════════════════════════════════
    //  handleServerMessage
    // ═════════════════════════════════════════════════════════════════════
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

            case "BID_HISTORY_RESPONSE" -> {
                try {
                    String payload = msg.getPayload();
                    if (payload == null || payload.isBlank()) return;

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

                    record HistoryEntry(String timeLabel, String username,
                                        String priceStr, double price) {}

                    List<HistoryEntry> entries = new ArrayList<>();
                    double maxBid = 0;

                    for (JsonElement el : arr) {
                        JsonObject row = el.getAsJsonObject();
                        String rawTime  = safeStr(row, "createdAt",
                                          safeStr(row, "bidTime", ""));
                        String username = safeStr(row, "username", "?");
                        double price    = safeDouble(row, "amount") > 0
                                          ? safeDouble(row, "amount")
                                          : safeDouble(row, "bidPrice");
                        String displayTime = convertUtcToVnTime(rawTime);
                        entries.add(new HistoryEntry(displayTime, username,
                                String.format("%,.0f VNĐ", price), price));
                        if (price > maxBid) maxBid = price;
                    }

                    final double finalMaxBid         = maxBid;
                    final List<HistoryEntry> finalEntries = entries;

                    Platform.runLater(() -> {
                        historyLoaded = true;
                        List<BidItem> rows = new ArrayList<>();
                        for (HistoryEntry e : finalEntries) {
                            rows.add(new BidItem(e.timeLabel(), e.username(), e.priceStr()));
                        }
                        bidHistory.setAll(rows);

                        if (finalMaxBid > 0) {
                            latestBid = finalMaxBid;
                            updateCurrentBidLabel(latestBid);
                            if (currentItem != null) currentItem.setCurrentBid(latestBid);
                        }

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

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto    = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId  = dto.get("productId").getAsString();
                    if (itemId == null || !itemId.equals(productId)) return;

                    double newBid     = dto.get("newBid").getAsDouble();
                    String bidderName = dto.has("bidderName")
                            ? dto.get("bidderName").getAsString() : "Unknown";

                    Platform.runLater(() -> {
                        latestBid = newBid;
                        if (currentItem != null) currentItem.setCurrentBid(newBid);
                        updateCurrentBidLabel(newBid);

                        String time = LocalTime.now().format(timeFmt);
                        bidHistory.add(0, new BidItem(time, bidderName,
                                String.format("%,.0f VNĐ", newBid)));
                        addChartPoint(time, newBid);
                        addLog(String.format("%s vừa đặt %,.0f VNĐ", bidderName, newBid));

                        // Toast bid mới (giữ nguyên)
                        try {
                            Stage stage = getStage();
                            if (stage != null) {
                                String itemName = currentItem != null ? currentItem.getName() : "";
                                ToastNotification.bid(stage, bidderName, itemName, newBid);
                            }
                        } catch (Exception ex) {
                            System.err.println("[LiveBidding] Toast lỗi: " + ex.getMessage());
                        }

                        // Auto-bid
                        handleAutoBidIfNeeded(bidderName);
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
                    JsonObject dto       = gson.fromJson(msg.getPayload(), JsonObject.class);
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

            // ── AUCTION_ENDED: delegate hoàn toàn sang AuctionEndedHandler ──
            case "AUCTION_ENDED" -> {
                Platform.runLater(() -> {
                    // Tắt countdown
                    if (countdownTimeline != null) countdownTimeline.stop();

                    // Tắt auto-bid nếu đang bật
                    if (autoBidActive) {
                        deactivateAutoBid();
                        addLog("⚡ Auto-bid đã tắt vì phiên kết thúc.");
                    }

                    // Delegate sang AuctionEndedHandler
                    // → dừng UI + hiển thị WinnerToastNotification đúng loại
                    endedHandler.handle(msg.getPayload());
                });
            }
        }
    }

    // ── UI helpers (giữ nguyên hoàn toàn) ────────────────────────────────

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

    /** Được gọi bởi: countdown hết giờ, AUCTION_ENDED, AuctionEndedHandler */
    private void onAuctionEnded() {
        if (lblCountdown != null) {
            lblCountdown.setText("KẾT THÚC!");
            lblCountdown.setStyle("-fx-text-fill:#a0aec0;");
        }
        if (txtBidAmount != null) txtBidAmount.setDisable(true);
        if (txtMaxBid    != null) txtMaxBid.setDisable(true);
        if (btnAutoBid   != null) btnAutoBid.setDisable(true);
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

    // ── Setup (giữ nguyên) ────────────────────────────────────────────────

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
                    ? Integer.parseInt(timeParts[2].trim()
                        .substring(0, Math.min(2, timeParts[2].trim().length())))
                    : 0;
            hour = (hour + 7) % 24;
            return String.format("%02d:%02d:%02d", hour, minute, second);
        } catch (Exception e) {
            return raw.length() >= 8 ? raw.substring(raw.length() - 8) : raw;
        }
    }

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
