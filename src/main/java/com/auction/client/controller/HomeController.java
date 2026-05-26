package com.auction.client.controller;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.handler.home.HomeMessageHandler;
import com.auction.client.handler.home.HomeTableSetup;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.SelectedProductSession;
import com.auction.client.session.UserSession;
import com.auction.shared.model.Entity.Item.Item;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;

public class HomeController implements Initializable {

    @FXML private TextField searchField;
    @FXML private Label     userLabel;

    @FXML private Label heroName;
    @FXML private Label heroBid;
    @FXML private Label heroStatus;
    @FXML private Label heroDesc;
    @FXML private ImageView heroImage;
    @FXML private Label heroPlaceholder;
    @FXML private Label heroTime;

    @FXML private TableView<Item>           auctionTable;
    @FXML private TableColumn<Item, String> colAuctionName;
    @FXML private TableColumn<Item, Double> colAuctionPrice;
    @FXML private TableColumn<Item, LocalDateTime> colAuctionEndTime; 
    @FXML private TableColumn<Item, String> colAuctionTime;           
    @FXML private TableColumn<Item, String> colAuctionStatus;
    @FXML private TableColumn<Item, Void>   colAuctionAction;

    private final ObservableList<Item> auctionList = FXCollections.observableArrayList();
    private Item heroItem;
    private Timeline heroTimeline; 

    private final NetworkClient client = NetworkClient.getInstance();
    private HomeMessageHandler messageHandler; 
    private NetworkClient.MessageListener listener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String username = UserSession.getInstance().getUsername();
        if (userLabel != null && username != null) {
            userLabel.setText("👤  " + username);
        }

        this.messageHandler = new HomeMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerMessage(msg);
        client.addListener(listener);

        // Liên kết dữ liệu danh sách động vào TableView trước khi thiết lập cấu trúc cột
        auctionTable.setItems(auctionList);

        // Khởi tạo cấu hình hiển thị cột bằng lớp Setup độc lập
        HomeTableSetup.setupTable(
            this, 
            auctionTable, 
            colAuctionName, 
            colAuctionPrice, 
            colAuctionEndTime, 
            colAuctionTime, 
            colAuctionStatus, 
            colAuctionAction
        );

        // Phát lệnh kéo dữ liệu từ Server về máy trạm
        client.send(new Message("GET_AUCTIONS", "{}"));
    }

    public void updateHeroCard(Item item) {
        if (item == null) return;
        this.heroItem = item;

        if (heroName   != null) heroName.setText(item.getName());
        if (heroBid    != null) heroBid.setText(String.format("%,.0f VND", item.getCurrentBid()));
        if (heroStatus != null) heroStatus.setText("Lot • " + item.getStatus());
        if (heroDesc   != null) {
            String desc = item.getDescription();
            heroDesc.setText(desc != null && !desc.isBlank() ? desc : (item.getSellerName() != null ? item.getSellerName() : ""));
        }

        if (heroImage != null) {
            String rawPath = item.getImagePath();
            String path = (rawPath != null && rawPath.contains("|")) ? rawPath.split("\\|")[0] : rawPath;
            
            if (path != null && !path.isBlank()) {
                try {
                    java.io.File imgFile = new java.io.File(path);
                    if (imgFile.exists()) {
                        javafx.scene.image.Image img = new javafx.scene.image.Image(imgFile.toURI().toString(), true);
                        heroImage.setImage(img);
                        if (heroPlaceholder != null) heroPlaceholder.setVisible(false);
                    } else {
                        heroImage.setImage(null);
                        if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
                    }
                } catch (Exception e) {
                    heroImage.setImage(null);
                    if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
                }
            } else {
                heroImage.setImage(null);
                if (heroPlaceholder != null) heroPlaceholder.setVisible(true);
            }
        }

        if (heroTimeline != null) {
            heroTimeline.stop();
        }

        if (heroTime != null) {
            if (item.getEndTime() == null) {
                heroTime.setText("");
                return;
            }

            heroTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
                java.time.LocalDateTime endTime = item.getEndTime();
                java.time.LocalDateTime now = java.time.LocalDateTime.now();

                if (now.isAfter(endTime)) {
                    heroTime.setText("⏳ ĐÃ KẾT THÚC");
                    heroTime.setStyle("-fx-text-fill: #e53e3e; -fx-font-size: 13; -fx-font-weight: bold;");
                    heroTimeline.stop();
                } else {
                    long days = java.time.temporal.ChronoUnit.DAYS.between(now, endTime);
                    long hours = java.time.temporal.ChronoUnit.HOURS.between(now, endTime) % 24;
                    long minutes = java.time.temporal.ChronoUnit.MINUTES.between(now, endTime) % 60;
                    long seconds = java.time.temporal.ChronoUnit.SECONDS.between(now, endTime) % 60;

                    heroTime.setStyle("-fx-text-fill: #fc8181; -fx-font-size: 13; -fx-font-weight: bold;");
                    if (days > 0) {
                        heroTime.setText(String.format("⏳ Còn %d ngày %02d:%02d:%02d", days, hours, minutes, seconds));
                    } else {
                        heroTime.setText(String.format("⏳ Còn %02d:%02d:%02d", hours, minutes, seconds));
                    }
                }
            }));
            heroTimeline.setCycleCount(Animation.INDEFINITE);
            heroTimeline.play();
        }
    }

    public void openDetail(Item item, ActionEvent event) {
        cleanUpBeforeLeave();
        SelectedProductSession.getInstance().setProductId(item.getId());
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
            auctionTable.setItems(auctionList);
        } else {
            auctionTable.setItems(auctionList.filtered(p -> p.getName().toLowerCase().contains(keyword)));
        }
    }

    @FXML public void onSideAuctionsClick(ActionEvent event) {
        auctionTable.setItems(auctionList);
    }

    @FXML public void onBidHistoryClick(ActionEvent event) {
        cleanUpBeforeLeave();
        SceneEngine.changeScene(event, "bid-history-view.fxml", "The Curator - Lịch sử đấu giá");
    }

    @FXML public void onSellerDashboardClick(ActionEvent event) {
        cleanUpBeforeLeave();
        SceneEngine.changeScene(event, "manage-product-view.fxml", "The Curator - Seller Dashboard");
    }

    @FXML public void onLogoutClick(ActionEvent event) {
        cleanUpBeforeLeave();
        UserSession.getInstance().logout();
        SelectedProductSession.getInstance().clear();
        SceneEngine.changeScene(event, "login-view.fxml", "The Curator - Đăng nhập");
    }

    private void cleanUpBeforeLeave() {
        client.removeListener(listener);
        if (heroTimeline != null) {
            heroTimeline.stop();
        }
    }

    @FXML public void onCardBidClicked(ActionEvent event) {}

    public ObservableList<Item> getAuctionList() { return auctionList; }
    public Item getHeroItem() { return heroItem; }
    public TableView<Item> getAuctionTable() { return auctionTable; }
}