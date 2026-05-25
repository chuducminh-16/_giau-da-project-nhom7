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
import com.auction.client.session.AutoBidSession;
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

    // ── FXML ─────────────────────────────────────────────────────────────
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

    // ── FXML Auto-Bid ─────────────────────────────────────────────────────
    @FXML private TextField txtMaxBid;
    @FXML private Button    btnAutoBid;
    @FXML private Label     lblAutoBidStatus;
    @FXML private Label     lblAutoBidInfo;
    @FXML private HBox      hboxAutoBidInfo;

    // ── Auto-bid state ────────────────────────────────────────────────────
    private boolean autoBidActive   = false;
    private double  autoBidMaxPrice = 0;

    // ── State ─────────────────────────────────────────────────────────────
    private Item     currentItem;
    private String   itemId;
    private XYChart.Series<String, Number> priceSeries;
    private Timeline countdownTimeline;

    // FIX BUG 2: -1 = chưa init (chờ PRODUCT_DETAIL_RESPONSE),
    //             0 = đã kết thúc thật sự.
    // Tránh handleAutoBidIfNeeded bị chặn sớm khi BID_UPDATE đến trước PRODUCT_DETAIL_RESPONSE.
    private long     secondsRemaining = -1;

    private double   latestBid     = 0;
    private boolean  historyLoaded = false;

    // FIX BUG 6: lưu pending auto-bid trigger từ BID_HISTORY khi currentItem chưa load xong,
    // thay thế hack Thread.sleep(1500) không đáng tin cậy.
    private String pendingAutoBidTriggerId   = null;
    private String pendingAutoBidTriggerName = null;

    private final ObservableList<BidItem> bidHistory    = FXCollections.observableArrayList();
    private final ObservableList<String>  notifications = FXCollections.observableArrayList();

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    // ── Gson builder ──────────────────────────────────────────────────────
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
            client.send(new Message("WATCH_AUCTION",
                    gson.toJson(Map.of("auctionId", itemId))));
        } else {
            addLog("Không tìm thấy thông tin sản phẩm!");
        }

        // ── Restore auto-bid state từ AutoBidSession ──────────────────────
        String myUserId = UserSession.getInstance().getUserId();
        AutoBidSession session = AutoBidSession.getInstance();
        if (session.isActiveForProduct(myUserId, itemId)) {
            autoBidActive   = true;
            autoBidMaxPrice = session.getMaxPrice();

            // Restore latestBid từ session để tính nextBidAmt đúng ngay lập tức
            latestBid = session.getLastKnownBid();

            if (txtMaxBid != null) {
                txtMaxBid.setText(String.valueOf((long) autoBidMaxPrice));
                txtMaxBid.setDisable(true);
            }
            if (btnAutoBid != null) {
                btnAutoBid.setText("⏹ Dừng Auto-bid");
                btnAutoBid.setStyle(
                        "-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                        "-fx-font-size: 13; -fx-font-weight: bold;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;");
            }
            setAutoBidStatusBadge(true);
            if (hboxAutoBidInfo != null) {
                hboxAutoBidInfo.setVisible(true);
                hboxAutoBidInfo.setManaged(true);
            }
            if (lblAutoBidInfo != null) {
                lblAutoBidInfo.setText(String.format(
                        "Tối đa: %,.0f VNĐ  —  Đã khôi phục sau khi vào lại", autoBidMaxPrice));
            }
            addLog(String.format("⚡ Auto-bid đã được khôi phục — tối đa %,.0f VNĐ", autoBidMaxPrice));
        }

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

    // ══════════════════════════════════════════════════════════════════════
    //  AUTO-BID TOGGLE
    // ══════════════════════════════════════════════════════════════════════
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

        // FIX BUG 1: lưu bidIncrement vào session (5 tham số)
        double savedIncrement = currentItem != null && currentItem.getBidIncrement() > 0
                ? currentItem.getBidIncrement() : 0;
        AutoBidSession.getInstance().activate(
                UserSession.getInstance().getUserId(),
                itemId,
                autoBidMaxPrice,
                currentBid,
                savedIncrement   // <-- param mới, khớp với AutoBidSession.activate(5 args)
        );

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

        AutoBidSession.getInstance().clear();

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

        // Xóa pending trigger nếu có
        pendingAutoBidTriggerId   = null;
        pendingAutoBidTriggerName = null;
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

    /**
     * Xử lý auto-bid phía CLIENT sau mỗi BID_UPDATE hoặc khi restore vào phòng.
     *
     * FIX BUG 2: guard dùng == 0 thay vì <= 0, vì -1 = "chưa init" không phải hết giờ.
     * FIX BUG 3: check lastBidderId không blank trước khi so sánh userId.
     * FIX BUG 4: bidIncrement lấy từ session (đã lưu 5-param) khi currentItem chưa có.
     */
    private void handleAutoBidIfNeeded(String lastBidderId, String lastBidderName) {
        if (!autoBidActive) return;
        if (currentItem == null) return;

        // FIX BUG 2: -1 nghĩa là chưa init (chờ server), KHÔNG phải hết giờ → không chặn.
        // Chỉ chặn khi == 0, tức là countdown đã đếm xong thật sự.
        if (secondsRemaining == 0) return;

        String myUserId   = UserSession.getInstance().getUserId();
        String myUsername = UserSession.getInstance().getUsername();

        // FIX BUG 3: chỉ so sánh userId khi lastBidderId có giá trị thật (không blank/null).
        // Nếu lastBidderId = "" (từ BID_HISTORY restore), fallback sang username.
        boolean iAmLeading;
        if (lastBidderId != null && !lastBidderId.isBlank()) {
            iAmLeading = myUserId.equals(lastBidderId);
        } else {
            // Fallback: so sánh username, nhưng lọc bỏ suffix " (auto)" do server gắn
            String cleanName = lastBidderName != null
                    ? lastBidderName.replace(" (auto)", "").trim() : "";
            iAmLeading = myUsername != null && myUsername.equals(cleanName);
        }

        if (iAmLeading) {
            if (lblAutoBidInfo != null) {
                lblAutoBidInfo.setText(String.format(
                        "Tối đa: %,.0f VNĐ  —  Bạn đang dẫn đầu!", autoBidMaxPrice));
            }
            return;
        }

        // FIX BUG 4: ưu tiên currentItem, fallback AutoBidSession (đã có bidIncrement từ activate 5-param)
        double increment;
        if (currentItem.getBidIncrement() > 0) {
            increment = currentItem.getBidIncrement();
        } else if (AutoBidSession.getInstance().getBidIncrement() > 0) {
            increment = AutoBidSession.getInstance().getBidIncrement();
        } else {
            increment = 500; // fallback tối thiểu
        }

        double nextBidAmt = latestBid + increment;

        if (nextBidAmt > autoBidMaxPrice) {
            addLog(String.format(
                    "⚡ Auto-bid dừng: giá tiếp theo (%,.0f) vượt giá tối đa (%,.0f).",
                    nextBidAmt, autoBidMaxPrice));
            deactivateAutoBid();
            try {
                Stage stage = (Stage) lblCountdown.getScene().getWindow();
                ToastNotification.warn(stage,
                        "⚡ Auto-bid đã dừng",
                        String.format("Giá vượt mức tối đa %,.0f VNĐ bạn đã đặt.", autoBidMaxPrice));
            } catch (Exception ignored) {}
            return;
        }

        addLog(String.format("🤖 Auto-bid: đang đặt %,.0f VNĐ...", nextBidAmt));
        if (lblAutoBidInfo != null) {
            lblAutoBidInfo.setText(String.format(
                    "Tối đa: %,.0f VNĐ  —  Vừa đặt tự động %,.0f VNĐ",
                    autoBidMaxPrice, nextBidAmt));
        }

        client.send(new Message("PLACE_BID", gson.toJson(Map.of(
                "productId", currentItem.getId(),
                "bidderId",  myUserId,
                "amount",    nextBidAmt
        ))));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  handleServerMessage
    // ══════════════════════════════════════════════════════════════════════
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

                        // FIX BUG 6: xử lý pending trigger đã lưu từ BID_HISTORY_RESPONSE
                        // (trường hợp BID_HISTORY về trước PRODUCT_DETAIL_RESPONSE)
                        if (autoBidActive && pendingAutoBidTriggerId != null && secondsRemaining > 0) {
                            handleAutoBidIfNeeded(pendingAutoBidTriggerId, pendingAutoBidTriggerName);
                            pendingAutoBidTriggerId   = null;
                            pendingAutoBidTriggerName = null;
                        }
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
                        String rawTime = safeStr(row, "createdAt",
                                safeStr(row, "bidTime", ""));
                        String username = safeStr(row, "username", "?");
                        double price = safeDouble(row, "amount") > 0
                                ? safeDouble(row, "amount")
                                : safeDouble(row, "bidPrice");
                        String displayTime = convertUtcToVnTime(rawTime);
                        entries.add(new HistoryEntry(displayTime, username,
                                String.format("%,.0f VNĐ", price), price));
                        if (price > maxBid) maxBid = price;
                    }

                    final double finalMaxBid = maxBid;
                    final List<HistoryEntry> finalEntries = entries;

                    // FIX BUG 5: history không có bidderId → dùng "" để handleAutoBidIfNeeded
                    // fallback sang username comparison (đã fix trong handleAutoBidIfNeeded)
                    final String topBidderUsername = finalEntries.isEmpty() ? "" : finalEntries.get(0).username();
                    final String topBidderId = ""; // history không trả bidderId

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
                            AutoBidSession.getInstance().updateLastKnownBid(latestBid);
                        }

                        priceSeries.getData().clear();
                        for (int i = finalEntries.size() - 1; i >= 0; i--) {
                            HistoryEntry e = finalEntries.get(i);
                            addChartPoint(e.timeLabel(), e.price());
                        }

                        if (!finalEntries.isEmpty()) {
                            addLog("Đã tải " + finalEntries.size() + " lần đặt giá trước đó.");
                        }

                        if (autoBidActive) {
                            // FIX BUG 6: thay Thread.sleep hack bằng pending trigger pattern.
                            // Nếu currentItem đã có → trigger ngay.
                            // Nếu chưa → lưu vào pending, PRODUCT_DETAIL_RESPONSE sẽ xử lý.
                            if (currentItem != null && secondsRemaining != 0) {
                                handleAutoBidIfNeeded(topBidderId, topBidderUsername);
                            } else {
                                pendingAutoBidTriggerId   = topBidderId;
                                pendingAutoBidTriggerName = topBidderUsername;
                                addLog("⚡ Auto-bid đang chờ thông tin sản phẩm tải xong...");
                            }
                        }
                    });
                } catch (Exception e) {
                    System.err.println("[LiveBidding] BID_HISTORY_RESPONSE lỗi: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId  = dto.get("productId").getAsString();

                    String currentItemId = itemId != null
                            ? itemId
                            : SelectedProductSession.getInstance().getProductId();

                    if (currentItemId == null || !currentItemId.equals(productId)) return;

                    double newBid     = dto.get("newBid").getAsDouble();
                    String bidderId   = dto.has("bidderId") && !dto.get("bidderId").isJsonNull()
                            ? dto.get("bidderId").getAsString() : "";
                    String bidderName = dto.has("bidderName")
                            ? dto.get("bidderName").getAsString() : "Unknown";

                    Platform.runLater(() -> {
                        if (itemId == null) itemId = productId;

                        latestBid = newBid;
                        if (currentItem != null) currentItem.setCurrentBid(newBid);
                        updateCurrentBidLabel(newBid);
                        AutoBidSession.getInstance().updateLastKnownBid(newBid);

                        String time = LocalTime.now().format(timeFmt);
                        bidHistory.add(0, new BidItem(time, bidderName,
                                String.format("%,.0f VNĐ", newBid)));
                        addChartPoint(time, newBid);
                        addLog(String.format("%s vừa đặt %,.0f VNĐ", bidderName, newBid));

                        try {
                            Stage stage = (Stage) lblCountdown.getScene().getWindow();
                            String itemName = currentItem != null ? currentItem.getName() : "";
                            ToastNotification.bid(stage, bidderName, itemName, newBid);
                        } catch (Exception ex) {
                            System.err.println("[LiveBidding] Toast loi: " + ex.getMessage());
                        }

                        handleAutoBidIfNeeded(bidderId, bidderName);
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
                    if (autoBidActive) {
                        deactivateAutoBid();
                        addLog("⚡ Auto-bid đã tắt vì phiên kết thúc.");
                    }
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

                        try {
                            Stage stage = (Stage) lblCountdown.getScene().getWindow();
                            String myName   = UserSession.getInstance().getUsername();
                            String itemName = currentItem != null ? currentItem.getName() : "";
                            if (winnerName != null && !winnerName.isEmpty()) {
                                ToastNotification.info(stage,
                                        "🏆 Kết quả đấu giá",
                                        String.format("%s thắng \"%s\"\nvới giá %,.0f VNĐ",
                                                winnerName, itemName, finalPrice));
                                if (winnerName.equals(myName))
                                    ToastNotification.win(stage, itemName, finalPrice);
                                else
                                    ToastNotification.lose(stage, itemName);
                            } else {
                                ToastNotification.info(stage,
                                        "Phiên kết thúc",
                                        "Không có người tham gia đặt giá.");
                            }
                        } catch (Exception ex) {
                            System.err.println("[LiveBidding] Toast winner loi: " + ex.getMessage());
                        }
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
            // FIX BUG 2: set về 0 rõ ràng (không để -1) để guard == 0 hoạt động đúng
            secondsRemaining = 0;
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
                        secondsRemaining = 0; // FIX BUG 2: không để âm
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
        if (secondsRemaining <= 60 && secondsRemaining > 0)
            lblCountdown.setStyle("-fx-text-fill:#fc8181; -fx-font-size:22; -fx-font-weight:bold;");
    }

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
        // FIX BUG 2: dùng == 0 thay vì <= 0 để -1 (chưa init) không bị chặn
        if (secondsRemaining == 0) {
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
        // Không clear AutoBidSession ở đây — để giữ state khi quay lại
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
                    ? Integer.parseInt(timeParts[2].trim().substring(0,
                        Math.min(2, timeParts[2].trim().length()))) : 0;
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