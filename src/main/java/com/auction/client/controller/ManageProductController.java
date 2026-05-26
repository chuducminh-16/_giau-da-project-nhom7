package com.auction.client.controller;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.handler.product.ProductFormValidator;
import com.auction.client.handler.product.ProductMessageHandler;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.auction.client.utils.ImageUploadHandler;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.JsonObject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;

/**
 * 👑 LỚP ĐIỀU KHIỂN GIAO DIỆN QUẢN LÝ SẢN PHẨM (MANAGE PRODUCT UI CONTROLLER)
 * - Đóng vai trò tiếp nhận tương tác nút bấm, đổ màu giao diện.
 * - Đã bổ sung tính năng tự động định dạng dấu chấm phân tách hàng nghìn cho tiền tệ.
 * - Đã nâng cấp liên kết phím mũi tên điều hướng nhanh giữa các ô nhập thời gian.
 */
public class ManageProductController implements Initializable {

    // ── 📌 KHAI BÁO CÁC PHẦN TỬ PHẢN XẠ FXML UI BINDING ──────────────────────
    @FXML private TextField txtName;
    @FXML private TextField txtStartingPrice;
    @FXML private TextField txtBidIncrement;
    @FXML private TextArea  txtDescription;

    @FXML private ImageView imgPreview1;
    @FXML private ImageView imgPreview2; // Giữ để tránh lỗi FXML Inject
    @FXML private ImageView imgPreview3; 
    @FXML private ImageView imgPreview4; 

    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dateEnd;

    @FXML private TextField txtStartHour;
    @FXML private TextField txtStartMinute;
    @FXML private TextField txtStartSecond;

    @FXML private TextField txtEndHour;
    @FXML private TextField txtEndMinute;
    @FXML private TextField txtEndSecond;

    @FXML private Button btnTypeArt;
    @FXML private Button btnTypeElectronics;
    @FXML private Button btnTypeVehicle;

    @FXML private TableView<Item>          tableProducts;
    @FXML private TableColumn<Item, String>        colName;
    @FXML private TableColumn<Item, Double>        colPrice;
    @FXML private TableColumn<Item, LocalDateTime> colTime;
    @FXML private TableColumn<Item, String>        colStatus;
    @FXML private TableColumn<Item, Double>        colCurrentBid;

    @FXML private Label statusLabel;

    // ── 📦 BIẾN TRẠNG THÁI VÀ BỘ HỖ TRỢ ỦY QUYỀN ─────────────────────────────
    private final ObservableList<Item> productData = FXCollections.observableArrayList();
    private volatile boolean isUploading = false;
    private ImageUploadHandler imageHandler;
    private String currentImagePath = ""; // Đường dẫn file lưu trữ trên Server sau khi upload thành công
    private String selectedType = "ART";  // Mặc định ban đầu là dòng Tranh Nghệ Thuật

    // Chuỗi mã màu CSS quản lý trạng thái kích hoạt của Tab lựa chọn loại hình sản phẩm
    private static final String BTN_ACTIVE_STYLE = "-fx-background-color: #4299e1; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";
    private static final String BTN_INACTIVE_STYLE = "-fx-background-color: #edf2f7; -fx-text-fill: #4a5568; -fx-background-radius: 8; -fx-cursor: hand; -fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";

    private NetworkClient client = NetworkClient.getInstance();
    private ProductMessageHandler messageHandler; // Lớp xử lý gói tin mạng và cấu trúc dữ liệu Gson
    private NetworkClient.MessageListener listener;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Thiết lập bộ phân phối tin nhắn mới
        this.messageHandler = new ProductMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerResponse(msg);

        client.removeListener(listener);
        client.addListener(listener);

        // Cấu hình Image Upload Handler ghim trực tiếp vào Khung xem trước chính (imgPreview1)
        imageHandler = new ImageUploadHandler(client, imgPreview1)
            .onSuccess(path -> {
                currentImagePath = path;
                isUploading = false;
                showStatus("✓ Upload ảnh thành công!", false);
            })
            .onError(err -> {
                isUploading = false;
                showStatus("⚠ " + err, true);
            });

        // Kích hoạt tính năng gõ tự thêm dấu chấm phân cách hàng nghìn
        setupCurrencyFormatter(txtStartingPrice);
        setupCurrencyFormatter(txtBidIncrement);

        setupTable();
        setupRowClickListener();
        setupTimeFieldListeners(); // Gọi thiết lập giới hạn số kiêm kích hoạt phím mũi tên
        updateTypeButtons("ART");
        loadMyProducts();
    }

    /**
     * 🟢 Bộ xử lý định dạng dấu chấm phân tách hàng nghìn khi người dùng nhập số trực tiếp
     */
    private void setupCurrencyFormatter(TextField field) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,###", symbols);

        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) return;

            // Loại bỏ tất cả ký tự không phải là số thuần túy
            String cleanString = newValue.replaceAll("[^\\d]", "");
            if (cleanString.isEmpty()) {
                field.setText("");
                return;
            }

            try {
                double parsed = Double.parseDouble(cleanString);
                String formatted = formatter.format(parsed);

                // Cập nhật text trên UI và đồng bộ con trỏ chuột ở cuối chuỗi
                javafx.application.Platform.runLater(() -> {
                    field.setText(formatted);
                    field.positionCaret(formatted.length());
                });
            } catch (NumberFormatException ignored) {}
        });
    }

    /**
     * Helper hỗ trợ chuyển số Double thuần từ đối tượng Item sang chuỗi có phân cách dấu chấm "."
     */
    private String formatDoubleToCurrencyString(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,###", symbols);
        return formatter.format(value);
    }

    private void setupTimeFieldListeners() {
        // 1. Giới hạn giá trị trần khi nhập số vào ô thời gian
        ProductFormValidator.limitTimeField(txtStartHour,   23);
        ProductFormValidator.limitTimeField(txtStartMinute, 59);
        ProductFormValidator.limitTimeField(txtStartSecond, 59);
        ProductFormValidator.limitTimeField(txtEndHour,     23);
        ProductFormValidator.limitTimeField(txtEndMinute,   59);
        ProductFormValidator.limitTimeField(txtEndSecond,   59);

        // 2. 🎯 KÍCH HOẠT ĐIỀU HƯỚNG PHÍM MŨI TÊN TRÁI/PHẢI GIỮA BỘ BA THỜI GIAN
        ProductFormValidator.linkTimeFieldsNavigation(txtStartHour, txtStartMinute, txtStartSecond);
        ProductFormValidator.linkTimeFieldsNavigation(txtEndHour, txtEndMinute, txtEndSecond);
    }

    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));

        colTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        colTime.setCellFactory(col -> new TableCell<>() {
            private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(fmt));
            }
        });

        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "ACTIVE", "RUNNING" -> "-fx-text-fill:#2ecc71; -fx-font-weight:bold;";
                    case "SOLD",   "FINISHED" -> "-fx-text-fill:#e74c3c; -fx-font-weight:bold;";
                    default                   -> "-fx-text-fill:#f1c40f; -fx-font-weight:bold;";
                });
            }
        });
        tableProducts.setItems(productData);
    }

    private void setupRowClickListener() {
        tableProducts.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;
            txtName.setText(newVal.getName());
            
            // Đổ giá trị số dạng dấu chấm (.) trực quan lên form khi chọn hàng trong bảng
            txtStartingPrice.setText(formatDoubleToCurrencyString(newVal.getStartingPrice()));
            txtBidIncrement.setText(formatDoubleToCurrencyString(newVal.getBidIncrement()));
            
            txtDescription.setText(newVal.getDescription() != null ? newVal.getDescription() : "");

            String itemType = newVal.getType() != null ? newVal.getType().toUpperCase() : "ART";
            selectedType = itemType;
            updateTypeButtons(itemType);

            if (newVal.getStartTime() != null) {
                dpStartDate.setValue(newVal.getStartTime().toLocalDate());
                txtStartHour.setText(String.format("%02d", newVal.getStartTime().getHour()));
                txtStartMinute.setText(String.format("%02d", newVal.getStartTime().getMinute()));
                txtStartSecond.setText(String.format("%02d", newVal.getStartTime().getSecond()));
            }
            if (newVal.getEndTime() != null) {
                dateEnd.setValue(newVal.getEndTime().toLocalDate());
                txtEndHour.setText(String.format("%02d", newVal.getEndTime().getHour()));
                txtEndMinute.setText(String.format("%02d", newVal.getEndTime().getMinute()));
                txtEndSecond.setText(String.format("%02d", newVal.getEndTime().getSecond()));
            }

            currentImagePath = "";
            String rawPath = newVal.getImagePath() != null ? newVal.getImagePath() : "";
            String firstPath = rawPath.contains("|") ? rawPath.split("\\|")[0] : rawPath;
            imageHandler.loadFromServer(firstPath); // Đổ ảnh từ thư mục máy chủ lên màn hình xem trước
        });
    }

    @FXML
    private void onAddProductClick() {
        if (isUploading) {
            showStatus("⏳ Vui lòng chờ upload ảnh xong...", false);
            return;
        }

        // Bóc sạch dấu chấm "." hiển thị trực quan trước khi đưa vào bộ Validator kiểm tra số hợp lệ
        String cleanStartPrice = txtStartingPrice.getText().trim().replaceAll("\\.", "");
        String cleanBidIncrement = txtBidIncrement.getText().trim().replaceAll("\\.", "");

        boolean isValid = ProductFormValidator.validateProductForm(
                txtName.getText().trim(), cleanStartPrice, cleanBidIncrement,
                dpStartDate, dateEnd, txtStartHour, txtStartMinute, txtStartSecond,
                txtEndHour, txtEndMinute, txtEndSecond, this::showStatus);
        
        if (!isValid) return;

        showStatus("Đang gửi...", false);
        LocalDateTime startDT = ProductFormValidator.buildDateTime(dpStartDate, txtStartHour, txtStartMinute, txtStartSecond, LocalDateTime.now());
        LocalDateTime endDT = ProductFormValidator.buildDateTime(dateEnd, txtEndHour, txtEndMinute, txtEndSecond, null);
        
        if (endDT == null) { showStatus("⚠ Vui lòng chọn ngày kết thúc.", true); return; }

        UserSession session = UserSession.getInstance();
        if (session == null || session.getUserId() == null) {
            showStatus("⚠ Lỗi: Phiên người dùng không hợp lệ!", true); return;
        }

        String payload = messageHandler.getGson().toJson(Map.of(
                "sellerId",     session.getUserId(),
                "name",         txtName.getText().trim(),
                "startPrice",   cleanStartPrice,
                "bidIncrement", cleanBidIncrement,
                "description",  txtDescription.getText().trim(),
                "startTime",    startDT.toString(),
                "endTime",      endDT.toString(),
                "imagePath",    currentImagePath,
                "type",         selectedType,
                "status",       "PENDING"
        ));
        client.send(new Message("ADD_PRODUCT", payload));
    }

    @FXML
    public void handleUpdateProduct(ActionEvent event) {
        if (isUploading) {
            showStatus("⏳ Vui lòng chờ upload ảnh xong...", false);
            return;
        }
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần sửa!", true); return; }

        // Bóc sạch dấu chấm "." hiển thị trực quan trước khi đưa vào bộ Validator kiểm tra số hợp lệ
        String cleanStartPrice = txtStartingPrice.getText().trim().replaceAll("\\.", "");
        String cleanBidIncrement = txtBidIncrement.getText().trim().replaceAll("\\.", "");

        boolean isValid = ProductFormValidator.validateProductForm(
                txtName.getText().trim(), cleanStartPrice, cleanBidIncrement,
                dpStartDate, dateEnd, txtStartHour, txtStartMinute, txtStartSecond,
                txtEndHour, txtEndMinute, txtEndSecond, this::showStatus);
        if (!isValid) return;

        LocalDateTime endDT = ProductFormValidator.buildDateTime(dateEnd, txtEndHour, txtEndMinute, txtEndSecond, selected.getEndTime());
        String imagePath = currentImagePath.isBlank() ? (selected.getImagePath() != null ? selected.getImagePath() : "") : currentImagePath;

        String payload = messageHandler.getGson().toJson(Map.of(
                "productId",    selected.getId(),
                "name",         txtName.getText().trim(),
                "startPrice",   cleanStartPrice,
                "bidIncrement", cleanBidIncrement,
                "description",  txtDescription.getText().trim(),
                "endTime",      endDT.toString(),
                "imagePath",    imagePath,
                "type",         selectedType
        ));
        client.send(new Message("UPDATE_PRODUCT", payload));
        showStatus("Đang cập nhật...", false);
    }

    @FXML
    public void onDeleteClick(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần xoá!", true); return; }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Xoá sản phẩm: " + selected.getName() + "?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xoá");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("DELETE_PRODUCT", messageHandler.getGson().toJson(Map.of("productId", selected.getId()))));
                showStatus("Đang xoá...", false);
            }
        });
    }

    @FXML private void onTypeArtClick(ActionEvent event) { selectedType = "ART"; updateTypeButtons("ART"); }
    @FXML private void onTypeElectronicsClick(ActionEvent event) { selectedType = "ELECTRONICS"; updateTypeButtons("ELECTRONICS"); }
    @FXML private void onTypeVehicleClick(ActionEvent event) { selectedType = "VEHICLE"; updateTypeButtons("VEHICLE"); }

    private void updateTypeButtons(String activeType) {
        if (btnTypeArt != null) btnTypeArt.setStyle(BTN_INACTIVE_STYLE);
        if (btnTypeElectronics != null) btnTypeElectronics.setStyle(BTN_INACTIVE_STYLE);
        if (btnTypeVehicle != null) btnTypeVehicle.setStyle(BTN_INACTIVE_STYLE);
        switch (activeType) {
            case "ART"         -> { if (btnTypeArt != null) btnTypeArt.setStyle(BTN_ACTIVE_STYLE); }
            case "ELECTRONICS" -> { if (btnTypeElectronics != null) btnTypeElectronics.setStyle(BTN_ACTIVE_STYLE); }
            case "VEHICLE"     -> { if (btnTypeVehicle != null) btnTypeVehicle.setStyle(BTN_ACTIVE_STYLE); }
        }
    }

    @FXML private void onSelectImageClick() {
        javafx.stage.Window window = imgPreview1.getScene().getWindow();
        isUploading = true; currentImagePath = "";
        showStatus("Đang upload ảnh...", false);
        imageHandler.pickAndUpload(window);
    }

    @FXML private void onSelectImage1Click() { onSelectImageClick(); }
    @FXML private void onSelectImage2Click() {}
    @FXML private void onSelectImage3Click() {}
    @FXML private void onSelectImage4Click() {}

    public void loadMyProducts() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.getUserId() != null)
            client.send(new Message("GET_MY_PRODUCTS", messageHandler.getGson().toJson(Map.of("sellerId", session.getUserId()))));
    }

    public void clearForm() {
        txtName.clear(); txtStartingPrice.clear(); txtBidIncrement.clear(); txtDescription.clear();
        dpStartDate.setValue(null); txtStartHour.clear(); txtStartMinute.clear(); txtStartSecond.clear();
        dateEnd.setValue(null); txtEndHour.clear(); txtEndMinute.clear(); txtEndSecond.clear();
        currentImagePath = ""; isUploading = false;
        if (imageHandler != null) imageHandler.clearPreview();
        selectedType = "ART"; updateTypeButtons("ART");
        tableProducts.getSelectionModel().clearSelection();
    }

    @FXML private void onBackHomeClick(ActionEvent event) {
        try { client.removeListener(listener); } catch (Exception ignored) {}
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    public void showStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError ? "-fx-text-fill:#e53e3e; -fx-font-size:12px;" : "-fx-text-fill:#38a169; -fx-font-size:12px;");
    }

    public String safeMsg(JsonObject root) { return root.has("message") ? root.get("message").getAsString() : "Lỗi không xác định"; }

    // ── ⚙️ DANH SÁCH GETTER GIÚP KẾT NỐI XUYÊN TẦNG HANDLER ──────────────────
    public ObservableList<Item> getProductData() { return productData; }
    public TableView<Item> getTableProducts() { return tableProducts; }
    public ImageUploadHandler getImageHandler() { return imageHandler; }
}