package com.auction.client.handler.admin;

import com.auction.client.controller.AdminController;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import java.util.Map;

/**
 * AdminTableSetup — Handler phụ trách việc khởi tạo cấu hình hiển thị cho các TableView.
 * Giúp giảm tải mã nguồn giao diện (UI Boilerplate) cho AdminController.
 */
public class AdminTableSetup {

    /**
     * Khởi tạo cấu trúc bảng quản lý Sản phẩm
     */
    public static void setupProductTable(
            AdminController controller,
            TableView<Map<String, Object>> tableProducts,
            TableColumn<Map<String, Object>, String> colProdId,
            TableColumn<Map<String, Object>, String> colProdName,
            TableColumn<Map<String, Object>, String> colProdType,
            TableColumn<Map<String, Object>, String> colProdPrice,
            TableColumn<Map<String, Object>, String> colProdSeller,
            TableColumn<Map<String, Object>, String> colProdStatus,
            TableColumn<Map<String, Object>, String> colProdEndTime,
            TableColumn<Map<String, Object>, Void> colProdAction
    ) {
        // Đổ dữ liệu từ Map vào các cột dựa trên key từ Server trả về
        colProdId.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("id"))));
        colProdName.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("name"))));
        colProdType.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("type"))));
        
        // Format hiển thị tiền tệ VNĐ cho cột giá sản phẩm
        colProdPrice.setCellValueFactory(d -> {
            double price = controller.safeDouble(d.getValue().get("current_price"));
            return new SimpleStringProperty(price > 0 ? String.format("%,.0f VNĐ", price) : "--");
        });
        
        colProdSeller.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("seller_id"))));
        colProdStatus.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("status"))));
        colProdEndTime.setCellValueFactory(d -> new SimpleStringProperty(controller.formatDateTime(controller.safeStr(d.getValue().get("end_time")))));

        // Tô màu chữ tùy theo trạng thái sản phẩm (RUNNING, OPEN, FINISHED,...)
        colProdStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(controller.getStatusStyle(item));
            }
        });

        // Tạo nút hành động "Xóa" cho từng sản phẩm
        colProdAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = new Button("🗑 Xóa");
            {
                btnDelete.setStyle("-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;" +
                        "-fx-font-size: 11; -fx-padding: 4 10;");
                btnDelete.setOnAction(e -> {
                    Map<String, Object> row = getTableView().getItems().get(getIndex());
                    controller.confirmDeleteProduct(row);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : btnDelete);
            }
        });
    }

    /**
     * Khởi tạo cấu trúc bảng quản lý Người dùng
     */
    public static void setupUserTable(
            AdminController controller,
            TableView<Map<String, Object>> tableUsers,
            TableColumn<Map<String, Object>, String> colUserId,
            TableColumn<Map<String, Object>, String> colUsername,
            TableColumn<Map<String, Object>, String> colUserEmail,
            TableColumn<Map<String, Object>, String> colUserRole,
            TableColumn<Map<String, Object>, String> colUserBalance,
            TableColumn<Map<String, Object>, Void> colUserAction
    ) {
        colUserId.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("id"))));
        colUsername.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("username"))));
        colUserEmail.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("email"))));
        colUserRole.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("role"))));
        
        // Format số dư tài khoản người dùng
        colUserBalance.setCellValueFactory(d -> {
            double bal = controller.safeDouble(d.getValue().get("balance"));
            return new SimpleStringProperty(String.format("%,.0f VNĐ", bal));
        });

        // Định dạng màu sắc phân biệt quyền hạn (ADMIN: đỏ, SELLER: vàng, BIDDER: xanh lá)
        colUserRole.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "ADMIN"  -> "-fx-text-fill:#e53e3e; -fx-font-weight:bold;";
                    case "SELLER" -> "-fx-text-fill:#d69e2e; -fx-font-weight:bold;";
                    default       -> "-fx-text-fill:#38a169; -fx-font-weight:bold;";
                });
            }
        });

        // Tạo nút hành động "Xóa" tài khoản (vô hiệu hóa nút nếu dòng đó trùng với tài khoản đang đăng nhập)
        colUserAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnDelete = new Button("🗑 Xóa");
            {
                btnDelete.setStyle("-fx-background-color: #e53e3e; -fx-text-fill: white;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;" +
                        "-fx-font-size: 11; -fx-padding: 4 10;");
                btnDelete.setOnAction(e -> {
                    Map<String, Object> row = getTableView().getItems().get(getIndex());
                    controller.confirmDeleteUser(row);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Map<String, Object> row = getTableView().getItems().get(getIndex());
                String rowId = controller.safeStr(row.get("id"));
                String myId  = com.auction.client.session.UserSession.getInstance().getUserId();
                btnDelete.setDisable(rowId.equals(myId)); // Chống tự xóa chính mình
                setGraphic(btnDelete);
            }
        });
    }

    /**
     * Khởi tạo cấu trúc bảng quản lý Phiên đấu giá
     */
    public static void setupAuctionTable(
            AdminController controller,
            TableView<Map<String, Object>> tableAuctions,
            TableColumn<Map<String, Object>, String> colAuctionId,
            TableColumn<Map<String, Object>, String> colAuctionItem,
            TableColumn<Map<String, Object>, String> colAuctionSeller,
            TableColumn<Map<String, Object>, String> colAuctionPrice,
            TableColumn<Map<String, Object>, String> colAuctionStatus,
            TableColumn<Map<String, Object>, String> colAuctionEnd,
            TableColumn<Map<String, Object>, Void> colAuctionAction
    ) {
        colAuctionId.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("id"))));
        colAuctionItem.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("itemName"))));
        colAuctionSeller.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("sellerId"))));
        colAuctionPrice.setCellValueFactory(d -> {
            double price = controller.safeDouble(d.getValue().get("currentPrice"));
            return new SimpleStringProperty(price > 0 ? String.format("%,.0f VNĐ", price) : "--");
        });
        colAuctionStatus.setCellValueFactory(d -> new SimpleStringProperty(controller.safeStr(d.getValue().get("status"))));
        colAuctionEnd.setCellValueFactory(d -> new SimpleStringProperty(controller.formatDateTime(controller.safeStr(d.getValue().get("endTime")))));

        colAuctionStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(controller.getStatusStyle(item));
            }
        });

        // Tạo nút hành động cưỡng chế "Đóng phiên" (Chỉ sáng lên khi phiên đang RUNNING hoặc OPEN)
        colAuctionAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnClose = new Button("⏹ Đóng");
            {
                btnClose.setStyle("-fx-background-color: #d69e2e; -fx-text-fill: white;" +
                        "-fx-background-radius: 6; -fx-cursor: hand;" +
                        "-fx-font-size: 11; -fx-padding: 4 10;");
                btnClose.setOnAction(e -> {
                    Map<String, Object> row = getTableView().getItems().get(getIndex());
                    controller.confirmCloseAuction(row);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Map<String, Object> row = getTableView().getItems().get(getIndex());
                String status = controller.safeStr(row.get("status"));
                btnClose.setDisable(!"RUNNING".equals(status) && !"OPEN".equals(status));
                setGraphic(btnClose);
            }
        });
    }
}