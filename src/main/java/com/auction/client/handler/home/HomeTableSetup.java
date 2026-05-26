package com.auction.client.handler.home;

import com.auction.client.controller.HomeController;
import com.auction.shared.model.Entity.Item.Item;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HomeTableSetup {

    public static void setupTable(
        HomeController controller,
        TableView<Item> auctionTable,
        TableColumn<Item, String> colName,
        TableColumn<Item, Double> colPrice,
        TableColumn<Item, LocalDateTime> colEndTime, 
        TableColumn<Item, String> colTime,           
        TableColumn<Item, String> colStatus,         
        TableColumn<Item, Void> colAction            
    ) {
        // 1. Cột Tên Sản phẩm
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        
        // 2. Cột Giá hiện tại
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentBid"));
        colPrice.setCellFactory(column -> new TableCell<Item, Double>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f VND", price));
                }
            }
        });

        // 3. Cột Mốc Thời gian kết thúc tĩnh
        colEndTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        colEndTime.setCellFactory(column -> new TableCell<Item, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.format(formatter));
                }
            }
        });

        // 4. Cột Đếm ngược "Còn lại" (Sử dụng liên kết chuỗi động, chống lỗi crash luồng)
        colTime.setCellValueFactory(cellData -> {
            Item currentItem = cellData.getValue();
            if (currentItem == null || currentItem.getEndTime() == null) {
                return new SimpleStringProperty("");
            }
            LocalDateTime endTime = currentItem.getEndTime();
            LocalDateTime now = LocalDateTime.now();
            if (now.isAfter(endTime)) {
                return new SimpleStringProperty("00:00:00");
            } else {
                long days = java.time.temporal.ChronoUnit.DAYS.between(now, endTime);
                long hours = java.time.temporal.ChronoUnit.HOURS.between(now, endTime) % 24;
                long minutes = java.time.temporal.ChronoUnit.MINUTES.between(now, endTime) % 60;
                long seconds = java.time.temporal.ChronoUnit.SECONDS.between(now, endTime) % 60;
                if (days > 0) {
                    return new SimpleStringProperty(String.format("%d ngày %02d:%02d:%02d", days, hours, minutes, seconds));
                } else {
                    return new SimpleStringProperty(String.format("%02d:%02d:%02d", hours, minutes, seconds));
                }
            }
        });
        colTime.setCellFactory(column -> new TableCell<Item, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #e53e3e; -fx-font-weight: bold;"); 
                }
            }
        });

        // 5. Cột Trạng thái (Đổ chữ màu sắc)
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colStatus.setCellFactory(col -> new TableCell<Item, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(item);
                if (item.equalsIgnoreCase("RUNNING") || item.equalsIgnoreCase("ACTIVE")) {
                    setStyle("-fx-text-fill: #38a169; -fx-font-weight: bold;"); 
                } else if (item.equalsIgnoreCase("FINISHED") || item.equalsIgnoreCase("SOLD")) {
                    setStyle("-fx-text-fill: #e53e3e; -fx-font-weight: bold;"); 
                } else {
                    setStyle("-fx-text-fill: #dd6b20; -fx-font-weight: bold;"); 
                }
            }
        });

        // 6. Cột Hành động (Sinh tự động nút "Tham gia")
        colAction.setCellFactory(col -> new TableCell<Item, Void>() {
            private final Button btn = new Button("Tham gia");
            {
                btn.setStyle("-fx-background-color: #48bb78; -fx-text-fill: white; " +
                             "-fx-background-radius: 6; -fx-cursor: hand; " +
                             "-fx-font-size: 12; -fx-padding: 4 12;");
                btn.setOnAction(event -> {
                    Item item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        controller.openDetail(item, event);
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btn);
                }
            }
        });
    }
}