package com.auction.client.controller;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.handler.product.ProductFormValidator;
import com.auction.client.handler.product.ProductMessageHandler;
import com.auction.client.handler.product.ProductTableSetup;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.auction.client.utils.CurrencyFormatter;
import com.auction.client.utils.ImageUploadHandler;
import com.auction.shared.model.Entity.Item.Item;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

/**
 * ════════════════════════════════════════════════════════════════════════
 * MANAGE PRODUCT CONTROLLER — Màn hình Quản lý Sản phẩm (Seller)
 * ════════════════════════════════════════════════════════════════════════
 *
 * ✅ FIX: Thêm @FXML cho handleDeleteProduct để FXML tìm thấy #handleDeleteProduct.
 *         FXML: onAction="#handleDeleteProduct"
 *         Trước đây thiếu @FXML → LoadException khi mở màn hình.
 * ════════════════════════════════════════════════════════════════════════
 */
public class ManageProductController implements Initializable {

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 1: KHAI BÁO CÁC PHẦN TỬ FXML
    // ════════════════════════════════════════════════════════════════════

    @FXML private TextField  txtName;
    @FXML private TextField  txtStartingPrice;
    @FXML private TextField  txtBidIncrement;
    @FXML private TextArea   txtDescription;

    @FXML private ImageView  imgPreview1;
    @FXML private ImageView  imgPreview2;
    @FXML private ImageView  imgPreview3;
    @FXML private ImageView  imgPreview4;

    @FXML private DatePicker dpStartDate;
    @FXML private TextField  txtStartHour;
    @FXML private TextField  txtStartMinute;
    @FXML private TextField  txtStartSecond;

    @FXML private DatePicker dateEnd;
    @FXML private TextField  txtEndHour;
    @FXML private TextField  txtEndMinute;
    @FXML private TextField  txtEndSecond;

    @FXML private Button btnTypeArt;
    @FXML private Button btnTypeElectronics;
    @FXML private Button btnTypeVehicle;

    @FXML private TableView<Item>              tableProducts;
    @FXML private TableColumn<Item, String>    colName;
    @FXML private TableColumn<Item, Double>    colPrice;
    @FXML private TableColumn<Item, LocalDateTime> colTime;
    @FXML private TableColumn<Item, String>    colStatus;
    @FXML private TableColumn<Item, Double>    colCurrentBid;

    @FXML private Label statusLabel;

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 2: KHAI BÁO BIẾN NỘI BỘ
    // ════════════════════════════════════════════════════════════════════

    private final ObservableList<Item> productData = FXCollections.observableArrayList();
    private volatile boolean isUploading = false;
    private ImageUploadHandler imageHandler;
    private String currentImagePath = "";
    private String selectedType = "ART";

    private static final String BTN_ACTIVE_STYLE =
        "-fx-background-color: #4299e1; -fx-text-fill: white;" +
        "-fx-background-radius: 8; -fx-cursor: hand;" +
        "-fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";

    private static final String BTN_INACTIVE_STYLE =
        "-fx-background-color: #edf2f7; -fx-text-fill: #4a5568;" +
        "-fx-background-radius: 8; -fx-cursor: hand;" +
        "-fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";

    private final NetworkClient client = NetworkClient.getInstance();
    private ProductMessageHandler messageHandler;
    private NetworkClient.MessageListener listener;

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 3: KHỞI TẠO
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        this.messageHandler = new ProductMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerResponse(msg);
        client.removeListener(listener);
        client.addListener(listener);

        imageHandler = new ImageUploadHandler(client, imgPreview1)
            .onSuccess(path -> {
                currentImagePath = path;
                isUploading = false;
                showStatus("Upload ảnh thành công!", false);
            })
            .onError(err -> {
                isUploading = false;
                showStatus(err, true);
            });

        CurrencyFormatter.attach(txtStartingPrice);
        CurrencyFormatter.attach(txtBidIncrement);

        ProductTableSetup tableSetup = new ProductTableSetup(
            tableProducts, colName, colPrice, colTime, colStatus, colCurrentBid,
            txtName, txtStartingPrice, txtBidIncrement,
            dpStartDate, txtStartHour, txtStartMinute, txtStartSecond,
            dateEnd, txtEndHour, txtEndMinute, txtEndSecond
        );
        tableSetup.setup(selectedItem -> {
            String type = selectedItem.getType() != null ? selectedItem.getType().toUpperCase() : "ART";
            selectedType = type;
            updateTypeButtons(type);
            currentImagePath = "";
            String rawPath = selectedItem.getImagePath() != null ? selectedItem.getImagePath() : "";
            String firstPath = rawPath.contains("|") ? rawPath.split("\\|")[0] : rawPath;
            imageHandler.loadFromServer(firstPath);
        });

        tableProducts.setItems(productData);
        setupTimeFieldListeners();
        updateTypeButtons("ART");
        loadMyProducts();
    }

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 4: GIỚI HẠN TRƯỜNG THỜI GIAN
    // ════════════════════════════════════════════════════════════════════

    private void setupTimeFieldListeners() {
        ProductFormValidator.limitTimeField(txtStartHour,   23);
        ProductFormValidator.limitTimeField(txtStartMinute, 59);
        ProductFormValidator.limitTimeField(txtStartSecond, 59);
        ProductFormValidator.limitTimeField(txtEndHour,     23);
        ProductFormValidator.limitTimeField(txtEndMinute,   59);
        ProductFormValidator.limitTimeField(txtEndSecond,   59);
        ProductFormValidator.linkTimeFieldsNavigation(txtStartHour, txtStartMinute, txtStartSecond);
        ProductFormValidator.linkTimeFieldsNavigation(txtEndHour, txtEndMinute, txtEndSecond);
    }

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 5: XỬ LÝ CÁC NÚT HÀNH ĐỘNG
    // ════════════════════════════════════════════════════════════════════

    /** Nút Add Product — validate rồi gửi ADD_PRODUCT lên Server */
    @FXML
    private void onAddProductClick() {
        if (isUploading) { showStatus("Vui lòng chờ upload ảnh xong...", false); return; }

        String cleanStartPrice   = txtStartingPrice.getText().trim().replaceAll("\\.", "");
        String cleanBidIncrement = txtBidIncrement.getText().trim().replaceAll("\\.", "");

        boolean isValid = ProductFormValidator.validateProductForm(
                txtName.getText().trim(), cleanStartPrice, cleanBidIncrement,
                dpStartDate, dateEnd,
                txtStartHour, txtStartMinute, txtStartSecond,
                txtEndHour, txtEndMinute, txtEndSecond,
                this::showStatus);
        if (!isValid) return;

        showStatus("Đang gửi...", false);

        LocalDateTime startDT = ProductFormValidator.buildDateTime(
                dpStartDate, txtStartHour, txtStartMinute, txtStartSecond, LocalDateTime.now());
        LocalDateTime endDT = ProductFormValidator.buildDateTime(
                dateEnd, txtEndHour, txtEndMinute, txtEndSecond, null);
        if (endDT == null) { showStatus("Vui lòng chọn ngày kết thúc.", true); return; }

        UserSession session = UserSession.getInstance();
        if (session == null || session.getUserId() == null) {
            showStatus("Lỗi: Phiên người dùng không hợp lệ!", true); return;
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

    /** Nút Update — validate rồi gửi UPDATE_PRODUCT lên Server */
    @FXML
    public void handleUpdateProduct(ActionEvent event) {
        if (isUploading) { showStatus("Vui lòng chờ upload ảnh xong...", false); return; }

        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần sửa!", true); return; }

        String cleanStartPrice   = txtStartingPrice.getText().trim().replaceAll("\\.", "");
        String cleanBidIncrement = txtBidIncrement.getText().trim().replaceAll("\\.", "");

        boolean isValid = ProductFormValidator.validateProductForm(
                txtName.getText().trim(), cleanStartPrice, cleanBidIncrement,
                dpStartDate, dateEnd,
                txtStartHour, txtStartMinute, txtStartSecond,
                txtEndHour, txtEndMinute, txtEndSecond,
                this::showStatus);
        if (!isValid) return;

        LocalDateTime endDT = ProductFormValidator.buildDateTime(
                dateEnd, txtEndHour, txtEndMinute, txtEndSecond, selected.getEndTime());
        String imagePath = currentImagePath.isBlank()
                ? (selected.getImagePath() != null ? selected.getImagePath() : "")
                : currentImagePath;

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

    /**
     * Nút Delete — xác nhận rồi gửi DELETE_PRODUCT lên Server.
     *
     * ✅ FIX: Thêm @FXML để FXML binding hoạt động.
     * FXML gọi onAction="#handleDeleteProduct" → method PHẢI có @FXML.
     * Nếu thiếu @FXML → JavaFX không tìm thấy → LoadException.
     */
    @FXML
    public void handleDeleteProduct(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần xoá!", true); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xoá sản phẩm: " + selected.getName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xoá");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                String payload = messageHandler.getGson().toJson(Map.of(
                        "productId", selected.getId(),
                        "sellerId",  UserSession.getInstance().getUserId()
                ));
                client.send(new Message("DELETE_PRODUCT", payload));
                showStatus("Đang xoá...", true); // đỏ — thao tác xóa
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 6: CHỌN LOẠI SẢN PHẨM
    // ════════════════════════════════════════════════════════════════════

    @FXML private void onTypeArtClick()         { updateTypeButtons("ART"); }
    @FXML private void onTypeElectronicsClick() { updateTypeButtons("ELECTRONICS"); }
    @FXML private void onTypeVehicleClick()     { updateTypeButtons("VEHICLE"); }

    private void updateTypeButtons(String type) {
        selectedType = type;
        if (btnTypeArt         != null) btnTypeArt.setStyle(BTN_INACTIVE_STYLE);
        if (btnTypeElectronics != null) btnTypeElectronics.setStyle(BTN_INACTIVE_STYLE);
        if (btnTypeVehicle     != null) btnTypeVehicle.setStyle(BTN_INACTIVE_STYLE);
        switch (type) {
            case "ART"         -> { if (btnTypeArt         != null) btnTypeArt.setStyle(BTN_ACTIVE_STYLE); }
            case "ELECTRONICS" -> { if (btnTypeElectronics != null) btnTypeElectronics.setStyle(BTN_ACTIVE_STYLE); }
            case "VEHICLE"     -> { if (btnTypeVehicle     != null) btnTypeVehicle.setStyle(BTN_ACTIVE_STYLE); }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 7: UPLOAD ẢNH
    // ════════════════════════════════════════════════════════════════════

    @FXML private void onSelectImageClick() {
        javafx.stage.Window window = imgPreview1.getScene().getWindow();
        isUploading = true;
        currentImagePath = "";
        showStatus("Đang upload ảnh...", false);
        imageHandler.pickAndUpload(window);
    }

    @FXML private void onSelectImage1Click() { onSelectImageClick(); }
    @FXML private void onSelectImage2Click() {}
    @FXML private void onSelectImage3Click() {}
    @FXML private void onSelectImage4Click() {}

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 8: TẢI DỮ LIỆU VÀ RESET FORM
    // ════════════════════════════════════════════════════════════════════

    public void loadMyProducts() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.getUserId() != null) {
            client.send(new Message("GET_MY_PRODUCTS",
                    messageHandler.getGson().toJson(Map.of("sellerId", session.getUserId()))));
        }
    }

    public void clearForm() {
        txtName.clear();
        txtStartingPrice.clear();
        txtBidIncrement.clear();
        txtDescription.clear();
        dpStartDate.setValue(null);
        txtStartHour.clear(); txtStartMinute.clear(); txtStartSecond.clear();
        dateEnd.setValue(null);
        txtEndHour.clear(); txtEndMinute.clear(); txtEndSecond.clear();
        currentImagePath = "";
        isUploading = false;
        if (imageHandler != null) imageHandler.clearPreview();
        selectedType = "ART";
        updateTypeButtons("ART");
        tableProducts.getSelectionModel().clearSelection();
    }

    @FXML private void onBackHomeClick(ActionEvent event) {
        try { client.removeListener(listener); } catch (Exception ignored) {}
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 9: HIỂN THỊ THÔNG BÁO TRẠNG THÁI
    // ════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị thông báo và tự xóa sau 4 giây.
     * isError=true → đỏ (xóa, lỗi) | isError=false → xanh (thêm, cập nhật)
     */
    public void showStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #e53e3e; -fx-font-size: 12px; -fx-font-weight: bold;"
                : "-fx-text-fill: #38a169; -fx-font-size: 12px; -fx-font-weight: bold;"
        );
        new Thread(() -> {
            try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                if (statusLabel.getText().equals(msg)) statusLabel.setText("");
            });
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════
    // PHẦN 10: GETTERS & HELPERS
    // ════════════════════════════════════════════════════════════════════

    public String safeMsg(JsonObject root) {
        return root.has("message") ? root.get("message").getAsString() : "Lỗi không xác định";
    }

    public ObservableList<Item> getProductData()   { return productData; }
    public TableView<Item>      getTableProducts() { return tableProducts; }
    public ImageUploadHandler   getImageHandler()  { return imageHandler; }
}
