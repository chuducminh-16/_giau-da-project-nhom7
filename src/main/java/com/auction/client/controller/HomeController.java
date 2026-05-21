package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Item;
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

    @FXML private TextField searchField;
    @FXML private Label     userLabel;

    // Hero card
    @FXML private Label heroName;
    @FXML private Label heroBid;
    @FXML private Label heroStatus;
    @FXML private Label heroDesc;

    // Bảng
    @FXML private TableView<Item>           auctionTable;
    @FXML private TableColumn<Item, String> colAuctionName;
    @FXML private TableColumn<Item, Double> colAuctionPrice;
    @FXML private TableColumn<Item, String> colAuctionTime;
    @FXML private TableColumn<Item, String> colAuctionStatus;
    @FXML private TableColumn<Item, Void>   colAuctionAction;

    private final ObservableList<Item> auctionList = FXCollections.observableArrayList();
    private Item heroItem;

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener = this::handleServerMessage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String username = UserSession.getInstance().getUsername();
        if (userLabel != null && username != null)
            userLabel.setText("👤  " + username);

        setupTable();
        client.addListener(listener);
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
        switch (msg.getType()) {

            case "AUCTIONS_LIST" -> {
                AuctionsResponse resp = gson.fromJson(msg.getPayload(), AuctionsResponse.class);
                Platform.runLater(() -> {
                    if (resp.auctions != null && !resp.auctions.isEmpty()) {
                        auctionList.setAll(resp.auctions);
                        updateHeroCard(resp.auctions.get(0));
                    }
                });
            }

            case "BID_UPDATE" -> {
                BidUpdateDto dto = gson.fromJson(msg.getPayload(), BidUpdateDto.class);
                Platform.runLater(() -> {
                    auctionList.stream()
                            .filter(p -> p.getId().equals(dto.productId))
                            .findFirst()
                            .ifPresent(p -> {
                                p.setCurrentBid(dto.newBid);
                                auctionTable.refresh();
                                if (heroItem != null && heroItem.getId().equals(dto.productId))
                                    updateHeroCard(p);
                            });
                });
            }
        }
    }

    private void updateHeroCard(Item item) {
        this.heroItem = item;
        if (heroName   != null) heroName.setText(item.getName());
        if (heroBid    != null) heroBid.setText(String.format("%,.0f VNĐ", item.getCurrentBid()));
        if (heroStatus != null) heroStatus.setText("Lot • " + item.getStatus());

        // ✅ FIX: dùng getDescription() thay getSellerName() làm mô tả hero card
        if (heroDesc != null) {
            String desc = item.getDescription();
            heroDesc.setText(desc != null && !desc.isBlank() ? desc :
                    (item.getSellerName() != null ? item.getSellerName() : ""));
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
            client.send(new Message("GET_AUCTIONS", "{}"));
            return;
        }
        auctionTable.setItems(auctionList.filtered(
                p -> p.getName().toLowerCase().contains(keyword)));
    }

    @FXML public void onSideAuctionsClick(ActionEvent event) {}

    @FXML public void onBidHistoryClick(ActionEvent event) {
        System.out.println("Mở lịch sử đấu giá");
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

    private record AuctionsResponse(boolean success, List<Item> auctions) {}
    private record BidUpdateDto(String productId, double newBid) {}
}
