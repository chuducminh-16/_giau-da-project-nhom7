package com.auction.client;

import com.auction.client.model.Product;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import java.io.File;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class ManageProductController implements Initializable {

    // --- FXML Components (Khai báo đúng ID trong Scene Builder) ---
    @FXML private TextField txtName, txtStartingPrice, txtBidIncrement;
    @FXML private TextArea txtDescription;
    @FXML private DatePicker dateEnd;
    @FXML private ImageView imgPreview;
    @FXML private DatePicker dpStartDate;
    @FXML private TableView<Product> tableProducts;
    @FXML private TableColumn<Product, String> colName, colStatus;
    @FXML private TableColumn<Product, Double> colPrice;
    @FXML private TableColumn<Product, LocalDateTime> colTime;
    @FXML private TableColumn<Product, Double> colCurrentBid;

    // Danh sách quan sát để tự động cập nhật TableView
    private ObservableList<Product> productData = FXCollections.observableArrayList();
    private String currentImagePath = "";

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 1. Kết nối các cột của bảng với các thuộc tính trong Model Product
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));
        colTime.setCellValueFactory(new PropertyValueFactory<>("formattedEndTime"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));

        // 2. Cấu hình đổ màu cho cột Status
        setupStatusColumnColor();

        // 3. Gắn danh sách dữ liệu vào bảng
        tableProducts.setItems(productData);

        // Lắng nghe sự kiện khi người dùng click vào 1 dòng trong TableView
        tableProducts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // Đổ dữ liệu từ đối tượng (newValue) lên các ô nhập liệu
                txtName.setText(newValue.getName()); // Tùy tên hàm getter của em
                txtStartingPrice.setText(String.valueOf(newValue.getStartingPrice()));
                txtBidIncrement.setText(String.valueOf(newValue.getBidIncrement()));
                txtDescription.setText(newValue.getDescription());

                // Đổ ngày tháng (Chuyển LocalDateTime -> LocalDate cho DatePicker)
                if (newValue.setStartTime() != null) {
                    dpStartDate.setValue(newValue.setStartTime().toLocalDate());
                }
                if (newValue.getEndTime() != null) {
                    dateEnd.setValue(newValue.getEndTime().toLocalDate());
                }
            }
        });

        // --- 2. CHÈN ĐOẠN ĐỊNH DẠNG THỜI GIAN VÀO ĐÂY ---
        colTime.setCellFactory(column -> new TableCell<Product, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    // Định dạng hiển thị: Ngày/Tháng/Năm Giờ:Phút
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    setText(item.format(formatter));
                }
            }
        });

        // Mock data để em chạy thử giao diện
        productData.add(new Product("Đồng hồ Rolex", 1000, 100, "Mô tả...", "ACTIVE", LocalDateTime.now().plusDays(1), "", LocalDateTime.now().plusDays(2)));
    }

    // --- A. Chức năng chọn ảnh ---
    @FXML
    private void onSelectImageClick() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn ảnh sản phẩm");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File selectedFile = fileChooser.showOpenDialog(null);

        if (selectedFile != null) {
            Image image = new Image(selectedFile.toURI().toString());
            imgPreview.setImage(image);
            currentImagePath = selectedFile.getAbsolutePath();
        }
    }

    // --- B. Chức năng Thêm sản phẩm ---
    @FXML
    private void onAddProductClick() {
        try {
            // Kiểm tra validate cơ bản
            if (txtName.getText().isEmpty() || dateEnd.getValue() == null) {
                showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng điền đầy đủ thông tin!");
                return;
            }

            String name = txtName.getText();
            double startPrice = Double.parseDouble(txtStartingPrice.getText());
            double increment = Double.parseDouble(txtBidIncrement.getText());
            String desc = txtDescription.getText();
            LocalDateTime end = LocalDateTime.of(dateEnd.getValue(), LocalTime.now());

            // Tạo Object mới
            Product newProduct = new Product(name, startPrice, increment, desc, "PENDING", end, currentImagePath, LocalDateTime.now());

            // TODO: Gửi newProduct qua Socket lên Server ở đây

            // Cập nhật lên giao diện
            productData.add(0, newProduct);
            clearForm();
            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã đăng bán sản phẩm!");

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi", "Giá tiền và bước giá phải là số!");
        }

        // 1. Lấy ngày từ DatePicker
        LocalDate startDate = dpStartDate.getValue();
        LocalDate endDate = dateEnd.getValue();

        // 2. Chuyển sang LocalDateTime (Ví dụ đặt mặc định là 8h sáng và 21h tối)
        LocalDateTime startDateTime = startDate.atTime(8, 0, 0);
        LocalDateTime endDateTime = endDate.atTime(21, 0, 0);

        // 3. Tạo đối tượng Product và set giá trị
        Product newProduct = new Product();
        newProduct.setStartTime(startDateTime);
        newProduct.setEndTime(endDateTime);
    }

    // --- C. Nút Back To Home ---
    @FXML
    private void onBackHomeClick(ActionEvent event) {
        // Quay về màn hình danh sách chính (home-view.fxml)
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // --- D. Chỉnh màu cột Status (Logic phần 4) ---
    private void setupStatusColumnColor() {
        colStatus.setCellFactory(column -> new TableCell<Product, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals("ACTIVE")) {
                        setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;"); // Xanh lá
                    } else if (item.equals("SOLD")) {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); // Đỏ
                    } else {
                        setStyle("-fx-text-fill: #f1c40f; -fx-font-weight: bold;"); // Vàng (Pending)
                    }
                }
            }
        });
    }

    private void clearForm() {
        txtName.clear();
        txtStartingPrice.clear();
        txtBidIncrement.clear();
        txtDescription.clear();
        dateEnd.setValue(null);
        imgPreview.setImage(null);
        currentImagePath = "";
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // --- E. Chức năng Xóa sản phẩm ---
    @FXML
    private void onDeleteClick(javafx.event.ActionEvent event) {
        // Lấy sản phẩm đang được chọn trong bảng
        Product selectedProduct = tableProducts.getSelectionModel().getSelectedItem();

        if (selectedProduct != null) {
            // Xóa khỏi danh sách hiển thị
            productData.remove(selectedProduct);

            // TODO: (Sau này) Gửi lệnh xóa lên Server qua Socket

            showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã xóa sản phẩm: " + selectedProduct.getName());
        } else {
            // Nếu người dùng chưa chọn dòng nào mà bấm Xóa
            showAlert(Alert.AlertType.WARNING, "Cảnh báo", "Vui lòng chọn một sản phẩm trong bảng để xóa!");
        }
    }

    // 2. Khi em viết hàm Thêm sản phẩm (Add Product), nhớ lấy giá trị từ nó:
    public void handleAddProduct(ActionEvent event) {
        LocalDate startDate = dpStartDate.getValue();
        LocalDate endDate = dateEnd.getValue();

        // Kiểm tra xem người dùng đã chọn ngày chưa
        if (startDate == null || endDate == null) {
            System.out.println("Vui lòng chọn đầy đủ ngày bắt đầu và kết thúc!");
            return;
        }

        // Lưu ý logic thực tế: Ngày bắt đầu không được lớn hơn ngày kết thúc
        if (startDate.isAfter(endDate)) {
            System.out.println("Ngày bắt đầu phải trước ngày kết thúc!");
            return;
        }
    }

// hàm cho update
    @FXML
    public void handleUpdateProduct(ActionEvent event) {
        // 1. Lấy ra sản phẩm đang được chọn (bôi xanh) trong bảng
        Product selectedProduct = tableProducts.getSelectionModel().getSelectedItem();

        if (selectedProduct == null) {
            System.out.println("Vui lòng chọn một sản phẩm trong bảng để sửa!");
            return;
        }

        try {
            // 2. Cập nhật thông tin mới từ Form vào đối tượng này
            selectedProduct.setName(txtName.getText());
            selectedProduct.setStartingPrice(Double.parseDouble(txtStartingPrice.getText()));
            selectedProduct.setBidIncrement(Double.parseDouble(txtBidIncrement.getText()));
            selectedProduct.setDescription(txtDescription.getText());
//            selectedProduct.setStartTime(dpStartDate.getValue());
//            selectedProduct.setEndTime(dateEnd.getValue());

            // Lấy sản phẩm đang được chọn trong bảng

            if (selectedProduct != null) {
                // Chuyển đổi ngày từ Form sang LocalDateTime
                if (dpStartDate.getValue() != null) {
                    selectedProduct.setStartTime(dpStartDate.getValue().atTime(8, 0, 0));
                }
                if (dateEnd.getValue() != null) {
                    selectedProduct.setEndTime(dateEnd.getValue().atTime(21, 0, 0));
                }
            }

            // 3. Làm mới bảng (Refresh) để hiển thị thông tin mới
            tableProducts.refresh();

            System.out.println("Cập nhật sản phẩm thành công!");

            // (Tùy chọn) Xóa trắng các ô nhập liệu sau khi update xong
            // clearForm();

        } catch (NumberFormatException e) {
            System.out.println("Lỗi: Giá tiền hoặc bước giá phải là số!");
        }
    }
}
