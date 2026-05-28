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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;

/**
 * ════════════════════════════════════════════════════════════════════════
 * 👑 MANAGE PRODUCT CONTROLLER — Màn hình Quản lý Sản phẩm (Seller)
 * ════════════════════════════════════════════════════════════════════════
 *
 * Chức năng chính:
 *   - Thêm sản phẩm mới (Add Product)
 *   - Cập nhật thông tin sản phẩm đã có (Update)
 *   - Xóa sản phẩm (Delete)
 *   - Xem danh sách sản phẩm của Seller hiện tại
 *   - Upload ảnh sản phẩm
 *
 * ✅ FIX: showStatus() giờ luôn hiển thị thông báo và tự xóa sau 4 giây.
 * ════════════════════════════════════════════════════════════════════════
 */
public class ManageProductController implements Initializable {

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 1: KHAI BÁO CÁC PHẦN TỬ FXML
    // ════════════════════════════════════════════════════════════════════

    // ── Form nhập liệu bên trái ──────────────────────────────────────
    @FXML private TextField txtName;          // Tên sản phẩm
    @FXML private TextField txtStartingPrice; // Giá khởi điểm (tự thêm dấu chấm phân nghìn)
    @FXML private TextField txtBidIncrement;  // Bước giá (tự thêm dấu chấm phân nghìn)
    @FXML private TextArea  txtDescription;   // Mô tả sản phẩm

    // ── Preview ảnh (chỉ dùng ảnh 1, các ảnh còn lại giữ để tránh lỗi FXML inject) ──
    @FXML private ImageView imgPreview1;
    @FXML private ImageView imgPreview2;
    @FXML private ImageView imgPreview3;
    @FXML private ImageView imgPreview4;

    // ── Thời gian bắt đầu ───────────────────────────────────────────
    @FXML private DatePicker dpStartDate;
    @FXML private TextField  txtStartHour;
    @FXML private TextField  txtStartMinute;
    @FXML private TextField  txtStartSecond;

    // ── Thời gian kết thúc ──────────────────────────────────────────
    @FXML private DatePicker dateEnd;
    @FXML private TextField  txtEndHour;
    @FXML private TextField  txtEndMinute;
    @FXML private TextField  txtEndSecond;

    // ── Nút chọn loại sản phẩm ──────────────────────────────────────
    @FXML private Button btnTypeArt;
    @FXML private Button btnTypeElectronics;
    @FXML private Button btnTypeVehicle;

    // ── Bảng danh sách sản phẩm bên phải ───────────────────────────
    @FXML private TableView<Item>           tableProducts;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, LocalDateTime> colTime;
    @FXML private TableColumn<Item, String> colStatus;
    @FXML private TableColumn<Item, Double> colCurrentBid;

    /**
     * ✅ FIX: statusLabel đã được chuyển sang cột trái (dưới 2 nút Add/Update)
     * và đặt visible="true" managed="true" trong FXML.
     * showStatus() giờ chỉ cần setText + setStyle, không cần toggle visible nữa.
     */
    @FXML private Label statusLabel;

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 2: KHAI BÁO BIẾN NỘI BỘ
    // ════════════════════════════════════════════════════════════════════

    /** Danh sách sản phẩm liên kết với TableView */
    private final ObservableList<Item> productData = FXCollections.observableArrayList();

    /** Cờ chống submit khi đang upload ảnh chưa xong */
    private volatile boolean isUploading = false;

    /** Bộ xử lý upload và hiển thị ảnh */
    private ImageUploadHandler imageHandler;

    /** Đường dẫn ảnh trên Server sau khi upload thành công */
    private String currentImagePath = "";

    /** Loại sản phẩm đang chọn — mặc định là ART */
    private String selectedType = "ART";

    /** Style CSS cho nút loại sản phẩm đang được chọn (nền xanh) */
    private static final String BTN_ACTIVE_STYLE =
        "-fx-background-color: #4299e1; -fx-text-fill: white;" +
        "-fx-background-radius: 8; -fx-cursor: hand;" +
        "-fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";

    /** Style CSS cho nút loại sản phẩm không được chọn (nền xám) */
    private static final String BTN_INACTIVE_STYLE =
        "-fx-background-color: #edf2f7; -fx-text-fill: #4a5568;" +
        "-fx-background-radius: 8; -fx-cursor: hand;" +
        "-fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";

    /** Kênh giao tiếp Socket với Server */
    private final NetworkClient client = NetworkClient.getInstance();

    /** Bộ xử lý phân tích gói tin phản hồi từ Server */
    private ProductMessageHandler messageHandler;

    /** Callback lắng nghe luồng mạng — lưu lại để giải phóng khi rời màn hình */
    private NetworkClient.MessageListener listener;

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 3: KHỞI TẠO
    // ════════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        // Khởi tạo bộ lắng nghe gói tin mạng
        this.messageHandler = new ProductMessageHandler(this);
        this.listener = msg -> messageHandler.handleServerResponse(msg);
        client.removeListener(listener); // Tránh đăng ký trùng lắp
        client.addListener(listener);

        // Cấu hình ImageUploadHandler gắn vào khung xem trước ảnh 1
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

        // Kích hoạt tính năng tự thêm dấu chấm phân cách hàng nghìn khi nhập giá
        setupCurrencyFormatter(txtStartingPrice);
        setupCurrencyFormatter(txtBidIncrement);

        // Cấu hình bảng, lắng nghe click dòng, giới hạn ô thời gian
        setupTable();
        setupRowClickListener();
        setupTimeFieldListeners();

        // Mặc định chọn loại ART và tải danh sách sản phẩm của seller
        updateTypeButtons("ART");
        loadMyProducts();
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 4: ĐỊNH DẠNG TIỀN TỆ
    // ════════════════════════════════════════════════════════════════════

    /**
     * Gắn listener tự động thêm dấu chấm phân cách hàng nghìn khi người dùng nhập số.
     * Ví dụ: nhập "1000000" → hiển thị "1.000.000"
     */
    private void setupCurrencyFormatter(TextField field) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,###", symbols);

        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || newValue.isEmpty()) return;

            // Loại bỏ mọi ký tự không phải số
            String cleanString = newValue.replaceAll("[^\\d]", "");
            if (cleanString.isEmpty()) { field.setText(""); return; }

            try {
                double parsed = Double.parseDouble(cleanString);
                String formatted = formatter.format(parsed);
                // Cập nhật text và đặt con trỏ về cuối
                Platform.runLater(() -> {
                    field.setText(formatted);
                    field.positionCaret(formatted.length());
                });
            } catch (NumberFormatException ignored) {}
        });
    }

    /**
     * Chuyển giá trị double thành chuỗi có dấu chấm phân nghìn để hiển thị trên form.
     * Ví dụ: 2000000.0 → "2.000.000"
     */
    private String formatDoubleToCurrencyString(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        DecimalFormat formatter = new DecimalFormat("#,###", symbols);
        return formatter.format(value);
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 5: THIẾT LẬP BẢNG VÀ LẮNG NGHE SỰ KIỆN
    // ════════════════════════════════════════════════════════════════════

    /** Cấu hình cột TableView và màu trạng thái */
    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));

        // Cột thời gian kết thúc — format dd/MM/yyyy HH:mm:ss
        colTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        colTime.setCellFactory(col -> new TableCell<>() {
            private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.format(fmt));
            }
        });

        // Cột trạng thái — tô màu theo giá trị
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

    /**
     * Khi người dùng click vào 1 dòng trong bảng,
     * tự động đổ toàn bộ dữ liệu sản phẩm đó lên form để chỉnh sửa.
     */
    private void setupRowClickListener() {
        tableProducts.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            txtName.setText(newVal.getName());
            // Hiển thị giá có dấu chấm phân nghìn
            txtStartingPrice.setText(formatDoubleToCurrencyString(newVal.getStartingPrice()));
            txtBidIncrement.setText(formatDoubleToCurrencyString(newVal.getBidIncrement()));
            txtDescription.setText(newVal.getDescription() != null ? newVal.getDescription() : "");

            // Đồng bộ loại sản phẩm
            String itemType = newVal.getType() != null ? newVal.getType().toUpperCase() : "ART";
            selectedType = itemType;
            updateTypeButtons(itemType);

            // Đổ thời gian bắt đầu
            if (newVal.getStartTime() != null) {
                dpStartDate.setValue(newVal.getStartTime().toLocalDate());
                txtStartHour.setText(String.format("%02d", newVal.getStartTime().getHour()));
                txtStartMinute.setText(String.format("%02d", newVal.getStartTime().getMinute()));
                txtStartSecond.setText(String.format("%02d", newVal.getStartTime().getSecond()));
            }

            // Đổ thời gian kết thúc
            if (newVal.getEndTime() != null) {
                dateEnd.setValue(newVal.getEndTime().toLocalDate());
                txtEndHour.setText(String.format("%02d", newVal.getEndTime().getHour()));
                txtEndMinute.setText(String.format("%02d", newVal.getEndTime().getMinute()));
                txtEndSecond.setText(String.format("%02d", newVal.getEndTime().getSecond()));
            }

            // Load ảnh từ Server
            currentImagePath = "";
            String rawPath = newVal.getImagePath() != null ? newVal.getImagePath() : "";
            String firstPath = rawPath.contains("|") ? rawPath.split("\\|")[0] : rawPath;
            imageHandler.loadFromServer(firstPath);
        });
    }

    /** Cấu hình giới hạn số và điều hướng phím mũi tên cho các ô giờ/phút/giây */
    private void setupTimeFieldListeners() {
        ProductFormValidator.limitTimeField(txtStartHour,   23);
        ProductFormValidator.limitTimeField(txtStartMinute, 59);
        ProductFormValidator.limitTimeField(txtStartSecond, 59);
        ProductFormValidator.limitTimeField(txtEndHour,     23);
        ProductFormValidator.limitTimeField(txtEndMinute,   59);
        ProductFormValidator.limitTimeField(txtEndSecond,   59);

        // Bấm mũi tên trái/phải để di chuyển giữa ô HH, MM, SS
        ProductFormValidator.linkTimeFieldsNavigation(txtStartHour, txtStartMinute, txtStartSecond);
        ProductFormValidator.linkTimeFieldsNavigation(txtEndHour, txtEndMinute, txtEndSecond);
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 6: XỬ LÝ CÁC NÚT HÀNH ĐỘNG
    // ════════════════════════════════════════════════════════════════════

    /** Xử lý nút "Add Product" — validate rồi gửi lệnh ADD_PRODUCT lên Server */
    @FXML
    private void onAddProductClick() {
        if (isUploading) { showStatus("⏳ Vui lòng chờ upload ảnh xong...", false); return; }

        // Bóc sạch dấu chấm trực quan trước khi validate số
        String cleanStartPrice    = txtStartingPrice.getText().trim().replaceAll("\\.", "");
        String cleanBidIncrement  = txtBidIncrement.getText().trim().replaceAll("\\.", "");

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

    /** Xử lý nút "Update" — validate rồi gửi lệnh UPDATE_PRODUCT lên Server */
    @FXML
    public void handleUpdateProduct(ActionEvent event) {
        if (isUploading) { showStatus("⏳ Vui lòng chờ upload ảnh xong...", false); return; }

        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("⚠ Vui lòng chọn sản phẩm cần sửa!", true); return; }

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

    /** Xử lý nút "Delete" — xác nhận rồi gửi lệnh DELETE_PRODUCT lên Server */
    @FXML
    public void onDeleteClick(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("⚠ Vui lòng chọn sản phẩm cần xoá!", true); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xoá sản phẩm: " + selected.getName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xoá");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("DELETE_PRODUCT",
                        messageHandler.getGson().toJson(Map.of("productId", selected.getId()))));
                showStatus("Đang xoá...", true); // đỏ — thao tác xóa
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 7: CHỌN LOẠI SẢN PHẨM
    // ════════════════════════════════════════════════════════════════════

    @FXML private void onTypeArtClick(ActionEvent event)         { selectedType = "ART";         updateTypeButtons("ART"); }
    @FXML private void onTypeElectronicsClick(ActionEvent event) { selectedType = "ELECTRONICS"; updateTypeButtons("ELECTRONICS"); }
    @FXML private void onTypeVehicleClick(ActionEvent event)     { selectedType = "VEHICLE";     updateTypeButtons("VEHICLE"); }

    /** Cập nhật màu nền nút loại sản phẩm — nút đang chọn nền xanh, còn lại nền xám */
    private void updateTypeButtons(String activeType) {
        if (btnTypeArt         != null) btnTypeArt.setStyle(BTN_INACTIVE_STYLE);
        if (btnTypeElectronics != null) btnTypeElectronics.setStyle(BTN_INACTIVE_STYLE);
        if (btnTypeVehicle     != null) btnTypeVehicle.setStyle(BTN_INACTIVE_STYLE);
        switch (activeType) {
            case "ART"         -> { if (btnTypeArt         != null) btnTypeArt.setStyle(BTN_ACTIVE_STYLE); }
            case "ELECTRONICS" -> { if (btnTypeElectronics != null) btnTypeElectronics.setStyle(BTN_ACTIVE_STYLE); }
            case "VEHICLE"     -> { if (btnTypeVehicle     != null) btnTypeVehicle.setStyle(BTN_ACTIVE_STYLE); }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 8: UPLOAD ẢNH
    // ════════════════════════════════════════════════════════════════════

    @FXML private void onSelectImageClick() {
        javafx.stage.Window window = imgPreview1.getScene().getWindow();
        isUploading = true;
        currentImagePath = "";
        showStatus("Đang upload ảnh...", false);
        imageHandler.pickAndUpload(window);
    }

    // Các ô ảnh 2-4 chưa dùng, giữ stub để FXML không báo lỗi missing handler
    @FXML private void onSelectImage1Click() { onSelectImageClick(); }
    @FXML private void onSelectImage2Click() {}
    @FXML private void onSelectImage3Click() {}
    @FXML private void onSelectImage4Click() {}

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 9: TẢI DỮ LIỆU VÀ RESET FORM
    // ════════════════════════════════════════════════════════════════════

    /** Gửi yêu cầu lấy danh sách sản phẩm của Seller hiện tại lên Server */
    public void loadMyProducts() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.getUserId() != null) {
            client.send(new Message("GET_MY_PRODUCTS",
                    messageHandler.getGson().toJson(Map.of("sellerId", session.getUserId()))));
        }
    }

    /** Xóa sạch toàn bộ form về trạng thái ban đầu sau khi thêm/xóa thành công */
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

    /** Nút Back Home — giải phóng listener rồi chuyển về trang chủ */
    @FXML private void onBackHomeClick(ActionEvent event) {
        try { client.removeListener(listener); } catch (Exception ignored) {}
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 10: HIỂN THỊ THÔNG BÁO TRẠNG THÁI
    // ════════════════════════════════════════════════════════════════════

    /**
     * ✅ FIX: Hiển thị thông báo lên statusLabel và tự xóa sau 4 giây.
     *
     * Trước đây label bị visible=false managed=false trong FXML và
     * showStatus() không bật lại → thông báo không bao giờ hiện.
     *
     * Giờ label đã luôn visible=true managed=true trong FXML.
     * showStatus() chỉ cần đổi text + màu chữ, và lên lịch tự xóa sau 4 giây.
     *
     * @param msg     Nội dung thông báo cần hiện
     * @param isError true = chữ đỏ (lỗi) | false = chữ xanh (thành công / thông tin)
     */
    public void showStatus(String msg, boolean isError) {
        if (statusLabel == null) return;

        // isError=true → chữ đỏ (dùng cho xóa và lỗi)
        // isError=false → chữ xanh lá (dùng cho thêm mới, cập nhật, thành công)
        statusLabel.setText(msg);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #e53e3e; -fx-font-size: 12px; -fx-font-weight: bold;"
                : "-fx-text-fill: #38a169; -fx-font-size: 12px; -fx-font-weight: bold;"
        );

        // ⏱ Tự động xóa thông báo sau 4 giây để giao diện không bị rác
        new Thread(() -> {
            try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
            Platform.runLater(() -> {
                // Chỉ xóa nếu label vẫn đang hiển thị đúng thông báo này
                // (tránh xóa nhầm thông báo mới hơn đã thay thế)
                if (statusLabel.getText().equals(msg)) {
                    statusLabel.setText("");
                }
            });
        }).start();
    }

    // ════════════════════════════════════════════════════════════════════
    // 📌 PHẦN 11: GETTERS & HELPERS CHO HANDLER
    // ════════════════════════════════════════════════════════════════════

    /**
     * Trích xuất chuỗi message từ JSON response của Server.
     * Dùng trong ProductMessageHandler khi cần lấy nội dung lỗi.
     */
    public String safeMsg(JsonObject root) {
        return root.has("message") ? root.get("message").getAsString() : "Lỗi không xác định";
    }

    /** Trả về danh sách sản phẩm để Handler cập nhật trực tiếp */
    public ObservableList<Item> getProductData() { return productData; }

    /** Trả về TableView để Handler xử lý chọn/bỏ chọn dòng */
    public TableView<Item> getTableProducts() { return tableProducts; }

    /** Trả về ImageUploadHandler để Handler điều phối sự kiện upload/tải ảnh */
    public ImageUploadHandler getImageHandler() { return imageHandler; }
}
