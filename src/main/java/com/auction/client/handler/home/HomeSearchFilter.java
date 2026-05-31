package com.auction.client.handler.home;

import com.auction.shared.model.Entity.Item.Item;
import javafx.collections.ObservableList;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

/**
 * HomeSearchFilter - Xu ly logic loc/tim kiem san pham trong trang chu.
 *
 * Tach ra tu HomeController de giam do phuc tap cua controller chinh.
 * Controller chi can goi HomeSearchFilter.apply() khi nguoi dung gao ENTER trong o tim kiem.
 */
public class HomeSearchFilter {

    private final TableView<Item>       table;
    private final ObservableList<Item>  allItems;

    /**
     * @param table    TableView hien thi danh sach san pham
     * @param allItems Danh sach goc chua tat ca san pham (khong loc)
     */
    public HomeSearchFilter(TableView<Item> table, ObservableList<Item> allItems) {
        this.table    = table;
        this.allItems = allItems;
    }

    /**
     * Ap dung bo loc theo tu khoa tim kiem.
     * - Neu tu khoa rong: hien lai toan bo danh sach goc
     * - Neu co tu khoa: chi giu lai san pham co ten chua tu khoa (khong phan biet hoa thuong)
     *
     * @param searchField TextField chua tu khoa tim kiem
     */
    public void apply(TextField searchField) {
        String keyword = searchField.getText().trim().toLowerCase();
        if (keyword.isEmpty()) {
            table.setItems(allItems);
        } else {
            table.setItems(allItems.filtered(item ->
                    item.getName() != null && item.getName().toLowerCase().contains(keyword)
            ));
        }
    }

    /**
     * Dat lai ve danh sach goc (huy bo bo loc).
     */
    public void reset() {
        table.setItems(allItems);
    }

    /**
     * Loc theo trang thai (ACTIVE/RUNNING/FINISHED/...).
     *
     * @param status Trang thai can loc, hoac null/empty de hien tat ca
     */
    public void filterByStatus(String status) {
        if (status == null || status.isBlank()) {
            table.setItems(allItems);
            return;
        }
        table.setItems(allItems.filtered(item ->
                item.getStatus() != null && item.getStatus().equalsIgnoreCase(status)
        ));
    }
}