package com.auction.client.handler.bidhistory;

import com.auction.client.controller.BidHistoryController;
import com.auction.client.controller.BidHistoryController.BidRecord;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * 🛠️ BỘ CẤU HÌNH BẢNG LỊCH SỬ (BID HISTORY TABLE SETUP)
 * - Nhiệm vụ: Tách biệt toàn bộ logic giao diện bảng, định dạng chuỗi và CSS cell.
 */
public class BidHistoryTableSetup {

    public static void setupTable(
            BidHistoryController controller,
            TableView<BidRecord> historyTable,
            TableColumn<BidRecord, String> colItemName,
            TableColumn<BidRecord, String> colMyBid,
            TableColumn<BidRecord, String> colFinalPrice,
            TableColumn<BidRecord, String> colStatus,
            TableColumn<BidRecord, String> colEndTime,
            TableColumn<BidRecord, String> colSeller
    ) {
        // 1. Cấu hình đổ dữ liệu chữ cơ bản cho các cột
        colItemName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().itemName()));
        colSeller.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().sellerName()));
        colEndTime.setCellValueFactory(d -> new SimpleStringProperty(controller.formatDateTime(d.getValue().endTime())));

        // 2. Định dạng cột giá tiền đấu giá của tôi
        colMyBid.setCellValueFactory(d -> new SimpleStringProperty(String.format("%,.0f VNĐ", d.getValue().myBid())));

        // 3. Định dạng cột giá chung cuộc (Xử lý trường hợp phiên chưa kết thúc)
        colFinalPrice.setCellValueFactory(d -> {
            double fp = d.getValue().finalPrice();
            return new SimpleStringProperty(fp > 0 ? String.format("%,.0f VNĐ", fp) : "Chưa kết thúc");
        });

        // 4. Cấu hình cột trạng thái: Check logic hết hạn từ Handler và nhuộm màu CSS
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(controller.getMessageHandler().resolveResult(d.getValue())));
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
                    default                 -> "-fx-text-fill:#718096; -fx-font-weight:bold;";
                });
            }
        });

        // 5. Nhuộm màu cột "Giá của tôi": Nếu thắng thì chữ màu xanh dương/lá nổi bật
        colMyBid.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                BidRecord rec = getTableView().getItems().get(getIndex());
                setText(item);
                boolean won = controller.getMessageHandler().isWinner(rec);
                setStyle(won ? "-fx-text-fill:#38a169; -fx-font-weight:bold;" : "-fx-text-fill:#2d3748;");
            }
        });
    }
}