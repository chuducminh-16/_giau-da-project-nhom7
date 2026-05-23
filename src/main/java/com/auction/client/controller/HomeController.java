package com.auction.client.controller;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class HomeController implements Initializable {

    @FXML private TextField searchField;
    @FXML private Label     userLabel;

    // Hero card
    @FXML private Label heroName;
    @FXML private Label heroBid;
    @FXML private Label heroStatus;
    @FXML private Label heroDesc;
    @FXML private ImageView heroImage;
    @FXML private Label heroPlaceholder;

    // Bảng
    @FXML private TableView<Item>           auctionTable;
    @FXML private TableColumn<Item, String> colAuctionName;
    @FXML private TableColumn<Item, Double> colAuctionPrice;
    @FXML private TableColumn<Item, String> colAuctionTime;
    @FXML private TableColumn<Item, String> colAuctionStatus;
    @FXML private TableColumn<Item, Void>   colAuctionAction;

    private final ObservableList<Item> auctionList = FXCollections.observableArrayList();
    private Item heroItem;

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    // ── Custom Gson deserializer cho abstract Item ────────────────────────
    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Item.class, (JsonDeserializer<Item>) (json, typeOfT, ctx) -> {
                    JsonObject obj = json.getAsJsonObject();
                    String type = "ART";
                    if (obj.has("type") && !obj.get("type").isJsonNull()) {
                        type = obj.get("type").getAsString().toUpperCase();
                    }
                    return switch (type) {
                        case "ELECTRONICS" -> ctx.deserialize(obj, Electronics.class);
                        case "VEHICLE"     -> ctx.deserialize(obj, Vehicle.class);
                        default            -> ctx.deserialize(obj, Art.class);
                    };
                })
                .create();
    }

    /**
     * Deserialize 1 JsonObject → đúng subclass Item theo field "type".
     * Dùng trong vòng lặp parse AUCTIONS_LIST thay vì cast trực tiếp.
     */
    private static Item deserializeItem(JsonObject obj, Gson gson) {
        try {
            String type = "ART";
            if (obj.has("type") && !obj.get("type").isJsonNull()) {
                type = obj.get("type").getAsString().toUpperCase();
            }
            System.out.println("[Home] Deserializing as type: " + type);
            System.out.println("[Home] Object keys: " + obj.keySet());

            Item item = switch (type) {
                case "ELECTRONICS" -> gson.fromJson(obj, Electronics.class);
                case "VEHICLE"     -> gson.fromJson(obj, Vehicle.class);
                default            -> gson.fromJson(obj, Art.class);
            };
            if (item != null) {
                System.out.println("[Home] ✓ Deserialized: " + item.getName() + " (id: " + item.getId() + ")");
            }
            return item;
        } catch (Exception e) {
            System.err.println("[HomeController] deserializeItem lỗi: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String username = UserSession.getInstance().getUsername();
        if (userLabel != null && username != null)
            userLabel.setText("👤  " + username);

        setupTable();
        client.addListener(listener);
        System.out.println("[Home] Listener added, sending GET_AUCTIONS request...");
        client.send(new Message("GET_AUCTIONS", "{}"));
    }

    private void setupTable() {
        colAuctionName.setCellValueFactory(new PropertyValueFactory<>("name"));

        colAuctionPrice.setCellValueFactory(new PropertyValueFactory<>("currentBid"));
        colAuctionPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.0f VNĐ", item));
            }
        });

        colAuctionTime.setCellValueFactory(new PropertyValueFactory<>("formattedEndTime"));

        colAuctionStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colAuctionStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "ACTIVE", "RUNNING" -> "-fx-text-fill:#38a169; -fx-font-weight:bold;";
                    case "SOLD", "FINISHED"  -> "-fx-text-fill:#e53e3e; -fx-font-weight:bold;";
                    default                  -> "-fx-text-fill:#d69e2e; -fx-font-weight:bold;";
                });
            }
        });

        colAuctionAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Tham gia");
            {
                btn.setStyle("-fx-background-color:#48bb78; -fx-text-fill:white;" +
                        "-fx-background-radius:6; -fx-cursor:hand;" +
                        "-fx-font-size:12; -fx-padding:4 12;");
                btn.setOnAction(e -> {
                    Item item = getTableView().getItems().get(getIndex());
                    openDetail(item, e);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btn);
            }
        });

        auctionTable.setItems(auctionList);
    }

    private void handleServerMessage(Message msg) {
        System.out.println("[Home] Received message type: " + msg.getType());
        System.out.println("[Home] Payload: " + msg.getPayload());

        switch (msg.getType()) {

            case "AUCTIONS_RESPONSE", "AUCTIONS_LIST", "GET_AUCTIONS_RESPONSE" -> {
                try {
                    JsonElement parsed = gson.fromJson(msg.getPayload(), JsonElement.class);
                    if (parsed == null || parsed.isJsonNull()) {
                        System.err.println("[Home] Payload is null/empty");
                        return;
                    }

                    JsonArray arr = null;
                    if (parsed.isJsonObject()) {
                        JsonObject root = parsed.getAsJsonObject();
                        if (root.has("auctions") && root.get("auctions").isJsonArray()) {
                            arr = root.getAsJsonArray("auctions");
                        } else if (root.has("items") && root.get("items").isJsonArray()) {
                            arr = root.getAsJsonArray("items");
                        }
                    } else if (parsed.isJsonArray()) {
                        arr = parsed.getAsJsonArray();
                    }

                    if (arr == null) {
                        System.err.println("[Home] Could not extract array from payload");
                        return;
                    }

                    System.out.println("[Home] Array size: " + arr.size());
                    List<Item> items = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonElement el = arr.get(i);
                        if (!el.isJsonObject()) {
                            System.err.println("[Home] Element " + i + " is not JSON object");
                            continue;
                        }
                        Item item = deserializeItem(el.getAsJsonObject(), gson);
                        if (item != null) {
                            items.add(item);
                            System.out.println("[Home] Added item: " + item.getName());
                        } else {
                            System.err.println("[Home] Failed to deserialize element " + i);
                        }
                    }

                    System.out.println("[Home] Total items deserialized: " + items.size());
                    Platform.runLater(() -> {
                        auctionList.setAll(items);
                        if (!items.isEmpty()) updateHeroCard(items.get(0));
                        System.out.println("[Home] ✓ Loaded " + items.size() + " auctions to table");
                    });
                } catch (Exception e) {
                    System.err.println("[Home] Lỗi parse AUCTIONS_RESPONSE: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            case "BID_UPDATE" -> {
                try {
                    JsonObject dto = gson.fromJson(msg.getPayload(), JsonObject.class);
                    String productId = dto.get("productId").getAsString();
                    double newBid    = dto.get("newBid").getAsDouble();

                    Platform.runLater(() -> {
                        auctionList.stream()
                                .filter(p -> p.getId().equals(productId))
                                .findFirst()
                                .ifPresent(p -> {
                                    p.setCurrentBid(newBid);
                                    auctionTable.refresh();
                                    if (heroItem != null && heroItem.getId().equals(productId))
                                        updateHeroCard(p);
                                });
                    });
                } catch (Exception e) {
                    System.err.println("[Home] Lỗi parse BID_UPDATE: " + e.getMessage());
                }
            }
        }
    }

    private void updateHeroCard(Item item) {
        this.heroItem = item;
        if (heroName   != null) heroName.setText(item.getName());
        if (heroBid    != null) heroBid.setText(String.format("%,.0f VNĐ", item.getCurrentBid()));
        if (heroStatus != null) heroStatus.setText("Lot • " + item.getStatus());
        if (heroDesc   != null) {
            String desc = item.getDescription();
            heroDesc.setText(desc != null && !desc.isBlank() ? desc :
                    (item.getSellerName() != null ? item.getSellerName() : ""));
        }

        // --- Xử lý hiển thị ảnh ---
        if (heroImage != null) {
            String path = item.getImagePath();
            if (path != null && !path.isBlank()) {
                try {
                    // Tải ảnh (background loading = true để tránh lag UI)
                    javafx.scene.image.Image img = new javafx.scene.image.Image("file:" + path, true);
                    heroImage.setImage(img);

                    // Ẩn icon camera khi đã có ảnh
                    if (heroPlaceholder != null) heroPlaceholder.setVisible(false);
                } catch (Exception e) {
                    System.err.println("[Home] Lỗi load ảnh hero: " + e.getMessage());
                    heroImage.setImage(null);
                    if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
                }
            } else {
                heroImage.setImage(null);
                if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
            }
        }
    }

    private void openDetail(Item item, ActionEvent event) {
        SelectedProductSession.getInstance().setProduct(item);
        client.removeListener(listener);
        SceneEngine.changeScene(event, "detail-view.fxml", "The Curator - Chi tiết sản phẩm");
    }

    @FXML public void onHeroBidClicked(ActionEvent event) {
        if (heroItem != null) openDetail(heroItem, event);
    }

    @FXML public void onRefreshAuctions(ActionEvent event) {
        client.send(new Message("GET_AUCTIONS", "{}"));
    }

    @FXML public void onSearchEnter(KeyEvent event) {
        if (event.getCode() != KeyCode.ENTER) return;
        String keyword = searchField.getText().trim().toLowerCase();
        if (keyword.isEmpty()) {
            // Reset về toàn bộ danh sách khi xoá keyword
            auctionTable.setItems(auctionList);
            return;
        }
        auctionTable.setItems(auctionList.filtered(
                p -> p.getName().toLowerCase().contains(keyword)));
    }

    @FXML public void onSideAuctionsClick(ActionEvent event) {
        // Reset filter khi bấm tab "Danh sách đấu giá"
        auctionTable.setItems(auctionList);
    }

    @FXML public void onBidHistoryClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "bid-history-view.fxml", "The Curator - Lịch sử đấu giá");
    }


    @FXML public void onSellerDashboardClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "manage-product-view.fxml", "The Curator - Seller Dashboard");
    }

    @FXML public void onLogoutClick(ActionEvent event) {
        client.removeListener(listener);
        UserSession.getInstance().logout();
        SelectedProductSession.getInstance().clear();
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }

    @FXML public void onCardBidClicked(ActionEvent event) {}
}