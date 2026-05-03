package com.auction.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class HomeController {

    // --- KHAI BÁO CÁC THÀNH PHẦN GIAO DIỆN (Phải khớp fx:id trong Scene Builder) ---

    @FXML
    private TextField searchField;

    @FXML
    private Button heroBidButton;

    // --- HÀM KHỞI TẠO (Chạy ngay khi giao diện vừa load xong) ---
    @FXML
    public void initialize() {
        System.out.println("Trang chủ đã tải xong! Sẵn sàng săn đồ cổ.");

        // Em có thể thiết lập thêm dữ liệu mặc định ở đây nếu cần
    }

    // --- CÁC HÀM XỬ LÝ SỰ KIỆN (Bắt hành động của người dùng) ---

    /**
     * Xử lý khi người dùng gõ xong ô tìm kiếm và bấm Enter
     */
    @FXML
    public void onSearchEnter(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            String keyword = searchField.getText();
            System.out.println("Hệ thống đang tìm kiếm từ khóa: " + keyword);
            // TODO: Viết code gọi API hoặc lọc danh sách sản phẩm tại đây
        }
    }

    /**
     * Xử lý khi bấm nút "Place Bid" ở sản phẩm nổi bật (Hero Section)
     */
    @FXML
    public void onHeroBidClicked(ActionEvent event) {
        System.out.println("Đã bấm Place Bid cho The Eames Prototype Archive!");
        // TODO: Viết code chuyển sang màn hình chi tiết sản phẩm (Detail View)
    }

    /**
     * Xử lý khi bấm nút "Bid Now" ở các thẻ sản phẩm nhỏ bên dưới
     */
    @FXML
    public void onCardBidClicked(ActionEvent event) {
        // Lấy ra nút vừa bị bấm để biết người dùng đang tương tác với thẻ nào
        Button clickedButton = (Button) event.getSource();
        System.out.println("Đã bấm đấu giá tại nút có ID là: " + clickedButton.getId());

        // TODO: Mở popup nhập giá hoặc chuyển trang chi tiết
    }
}
