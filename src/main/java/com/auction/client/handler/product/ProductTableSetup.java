package com.auction.client.handler.product;

import com.auction.client.utils.CurrencyFormatter;
import com.auction.shared.model.Entity.Item.Item;

import javafx.scene.control.DatePicker;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ProductTableSetup - Quan ly cau hinh hien thi cot bang san pham (Seller Dashboard).
 *
 * Trach nhiem:
 *   - Cau hinh CellValueFactory cho tung cot TableView
 *   - To mau cot trang thai (ACTIVE/SOLD/PENDING)
 *   - Format cot thoi gian ket thuc (dd/MM/yyyy HH:mm:ss)
 *   - Dong bo du lieu dong duoc chon len form nhap lieu
 *
 * Tach ra tu ManageProductController de giam do phuc tap cua file chinh.
 */
public class ProductTableSetup {

    private final TableView<Item>             tableProducts;
    private final TableColumn<Item, String>   colName;
    private final TableColumn<Item, Double>   colPrice;
    private final TableColumn<Item, LocalDateTime> colTime;
    private final TableColumn<Item, String>   colStatus;
    private final TableColumn<Item, Double>   colCurrentBid;

    // Form fields can dong bo khi click dong bang
    private final TextField txtName;
    private final TextField txtStartingPrice;
    private final TextField txtBidIncrement;
    private final DatePicker dpStartDate;
    private final TextField txtStartHour;
    private final TextField txtStartMinute;
    private final TextField txtStartSecond;
    private final DatePicker dateEnd;
    private final TextField txtEndHour;
    private final TextField txtEndMinute;
    private final TextField txtEndSecond;

    public ProductTableSetup(
            TableView<Item> tableProducts,
            TableColumn<Item, String> colName,
            TableColumn<Item, Double> colPrice,
            TableColumn<Item, LocalDateTime> colTime,
            TableColumn<Item, String> colStatus,
            TableColumn<Item, Double> colCurrentBid,
            TextField txtName,
            TextField txtStartingPrice,
            TextField txtBidIncrement,
            DatePicker dpStartDate,
            TextField txtStartHour, TextField txtStartMinute, TextField txtStartSecond,
            DatePicker dateEnd,
            TextField txtEndHour, TextField txtEndMinute, TextField txtEndSecond
    ) {
        this.tableProducts   = tableProducts;
        this.colName         = colName;
        this.colPrice        = colPrice;
        this.colTime         = colTime;
        this.colStatus       = colStatus;
        this.colCurrentBid   = colCurrentBid;
        this.txtName         = txtName;
        this.txtStartingPrice = txtStartingPrice;
        this.txtBidIncrement = txtBidIncrement;
        this.dpStartDate     = dpStartDate;
        this.txtStartHour    = txtStartHour;
        this.txtStartMinute  = txtStartMinute;
        this.txtStartSecond  = txtStartSecond;
        this.dateEnd         = dateEnd;
        this.txtEndHour      = txtEndHour;
        this.txtEndMinute    = txtEndMinute;
        this.txtEndSecond    = txtEndSecond;
    }

    /**
     * Cau hinh toan bo TableView: cac cot, mau sac, va bo lang nghe click dong.
     *
     * @param onRowSelected Callback nhan Item duoc chon - dung de xu ly them (upload anh, v.v.)
     */
    public void setup(java.util.function.Consumer<Item> onRowSelected) {
        setupColumns();
        setupRowClickListener(onRowSelected);
    }

    // =========================================================================
    // Cau hinh cot
    // =========================================================================

    private void setupColumns() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));

        // Cot thoi gian ket thuc: format dd/MM/yyyy HH:mm:ss
        colTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        colTime.setCellFactory(col -> new TableCell<>() {
            private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(fmt));
            }
        });

        // Cot trang thai: to mau theo gia tri
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "ACTIVE", "RUNNING"  -> "-fx-text-fill:#2ecc71; -fx-font-weight:bold;";
                    case "SOLD",   "FINISHED" -> "-fx-text-fill:#e74c3c; -fx-font-weight:bold;";
                    default                   -> "-fx-text-fill:#f1c40f; -fx-font-weight:bold;";
                });
            }
        });
    }

    // =========================================================================
    // Dong bo form khi click dong bang
    // =========================================================================

    private void setupRowClickListener(java.util.function.Consumer<Item> onRowSelected) {
        tableProducts.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            // Dien thong tin co ban vao form
            txtName.setText(newVal.getName());
            txtStartingPrice.setText(CurrencyFormatter.format(newVal.getStartingPrice()));
            txtBidIncrement.setText(CurrencyFormatter.format(newVal.getBidIncrement()));

            // Dong bo loai san pham (type)
            String itemType = newVal.getType() != null ? newVal.getType().toUpperCase() : "ART";

            // Dong bo thoi gian bat dau
            if (newVal.getStartTime() != null) {
                dpStartDate.setValue(newVal.getStartTime().toLocalDate());
                txtStartHour.setText(String.format("%02d", newVal.getStartTime().getHour()));
                txtStartMinute.setText(String.format("%02d", newVal.getStartTime().getMinute()));
                txtStartSecond.setText(String.format("%02d", newVal.getStartTime().getSecond()));
            }

            // Dong bo thoi gian ket thuc
            if (newVal.getEndTime() != null) {
                dateEnd.setValue(newVal.getEndTime().toLocalDate());
                txtEndHour.setText(String.format("%02d", newVal.getEndTime().getHour()));
                txtEndMinute.setText(String.format("%02d", newVal.getEndTime().getMinute()));
                txtEndSecond.setText(String.format("%02d", newVal.getEndTime().getSecond()));
            }

            // Goi callback tuy chinh (vi du: load anh, cap nhat type buttons)
            if (onRowSelected != null) onRowSelected.accept(newVal);
        });
    }
}