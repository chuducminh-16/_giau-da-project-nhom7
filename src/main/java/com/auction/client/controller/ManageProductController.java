package com.auction.client.controller;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import com.auction.client.SceneEngine;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.auction.client.utils.ImageUploadHandler;
import com.auction.shared.model.Entity.Item.Art;
import com.auction.shared.model.Entity.Item.Electronics;
import com.auction.shared.model.Entity.Item.Item;
import com.auction.shared.model.Entity.Item.Vehicle;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;

public class ManageProductController implements Initializable {

    @FXML private TextField txtName;
    @FXML private TextField txtStartingPrice;
    @FXML private TextField txtBidIncrement;
    @FXML private TextArea  txtDescription;

    // ── ẢNH: slot preview chính (upload qua server) ───────────────────────
    @FXML private ImageView imgPreview1;
    // Giữ các field cũ để FXML không bị lỗi inject – không dùng trực tiếp
    @FXML private ImageView imgPreview2;
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

    // ── Nút chọn loại sản phẩm (giữ nguyên để FXML không lỗi) ───────────
    @FXML private Button btnTypeArt;
    @FXML private Button btnTypeElectronics;
    @FXML private Button btnTypeVehicle;

    @FXML private TableView<Item>                  tableProducts;
    @FXML private TableColumn<Item, String>        colName;
    @FXML private TableColumn<Item, Double>        colPrice;
    @FXML private TableColumn<Item, LocalDateTime> colTime;
    @FXML private TableColumn<Item, String>        colStatus;
    @FXML private TableColumn<Item, Double>        colCurrentBid;

    @FXML private Label statusLabel;

    private final ObservableList<Item> productData = FXCollections.observableArrayList();

    // ── ẢNH ──────────────────────────────────────────────────────────────
    // Thêm vào sau dòng: private String currentImagePath = "";
    private volatile boolean isUploading = false;
    private ImageUploadHandler imageHandler;
    private String currentImagePath = "";   // server path sau khi upload
    // ─────────────────────────────────────────────────────────────────────

    // ── Loại sản phẩm ─────────────────────────────────────────────────────
    

    

    // ── Loại sản phẩm đang chọn (MỚI THÊM) ────────────────────────────
    private String selectedType = "ART"; // mặc định ART

    // Style cho nút đang active và không active
    private static final String BTN_ACTIVE_STYLE =
            "-fx-background-color: #4299e1; -fx-text-fill: white;" +
                    "-fx-background-radius: 8; -fx-cursor: hand;" +
                    "-fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";
    private static final String BTN_INACTIVE_STYLE =
            "-fx-background-color: #edf2f7; -fx-text-fill: #4a5568;" +
                    "-fx-background-radius: 8; -fx-cursor: hand;" +
                    "-fx-font-size: 13; -fx-font-weight: bold; -fx-border-radius: 8;";

    private final Gson gson = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    // ─────────────────────────────────────────────────────────────────────

    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Item.class, (JsonDeserializer<Item>) (json, typeOfT, ctx) ->
                        deserializeItem(json.getAsJsonObject()))
                .create();
    }

    private static Item deserializeItem(JsonObject obj) {
        try {
            String type = "ART";
            if (obj.has("type") && !obj.get("type").isJsonNull())
                type = obj.get("type").getAsString().toUpperCase();
            Gson plain = new Gson();
            return switch (type) {
                case "ELECTRONICS" -> plain.fromJson(obj, Electronics.class);
                case "VEHICLE"     -> plain.fromJson(obj, Vehicle.class);
                default            -> plain.fromJson(obj, Art.class);
            };
        } catch (Exception e) { return null; }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client.removeListener(listener);
        client.addListener(listener);

        // Khởi tạo imageHandler — dùng imgPreview1 làm preview chính
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

        setupTable();
        setupRowClickListener();
        setupTimeFieldListeners();
        updateTypeButtons("ART");
        loadMyProducts();
    }

    // ── Loại sản phẩm ─────────────────────────────────────────────────────
    @FXML private void onTypeArtClick(ActionEvent event) {
        selectedType = "ART";
        updateTypeButtons("ART");
    }

    @FXML private void onTypeElectronicsClick(ActionEvent event) {
        selectedType = "ELECTRONICS";
        updateTypeButtons("ELECTRONICS");
    }

    @FXML private void onTypeVehicleClick(ActionEvent event) {
        selectedType = "VEHICLE";
        updateTypeButtons("VEHICLE");
    }

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

    // ── Nút chọn ảnh ─────────────────────────────────────────────────────
    // Hỗ trợ cả 2 tên handler: onSelectImageClick (mới) và onSelectImage1Click (cũ)
    @FXML private void onSelectImageClick() {
        javafx.stage.Window window = imgPreview1.getScene().getWindow();
        isUploading = true;
        currentImagePath = "";           // xóa path cũ khi chọn ảnh mới
        showStatus("Đang upload ảnh...", false);
        imageHandler.pickAndUpload(window);
    }

    @FXML private void onSelectImage1Click() {
        onSelectImageClick();
    }

    // Giữ handler cũ để FXML không lỗi nếu còn nút ảnh 2-4
    @FXML private void onSelectImage2Click() { /* không dùng nữa */ }
    @FXML private void onSelectImage3Click() { /* không dùng nữa */ }
    @FXML private void onSelectImage4Click() { /* không dùng nữa */ }
    // ─────────────────────────────────────────────────────────────────────

    private void setupTimeFieldListeners() {
        limitTimeField(txtStartHour,   23);
        limitTimeField(txtStartMinute, 59);
        limitTimeField(txtStartSecond, 59);
        limitTimeField(txtEndHour,     23);
        limitTimeField(txtEndMinute,   59);
        limitTimeField(txtEndSecond,   59);
    }

    private void limitTimeField(TextField field, int maxValue) {
        if (field == null) return;
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*")) {
                field.setText(newVal.replaceAll("[^\\d]", ""));
                return;
            }
            if (newVal.length() > 2) { field.setText(oldVal); return; }
            if (!newVal.isEmpty()) {
                try {
                    if (Integer.parseInt(newVal) > maxValue)
                        field.setText(String.valueOf(maxValue));
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));

        colTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        colTime.setCellFactory(col -> new TableCell<>() {
            private final DateTimeFormatter fmt =
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
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
            txtStartingPrice.setText(String.valueOf(newVal.getStartingPrice()));
            txtBidIncrement.setText(String.valueOf(newVal.getBidIncrement()));
            String desc = newVal.getDescription();
            txtDescription.setText(desc != null ? desc : "");

            // Cập nhật nút type theo loại sản phẩm
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

            // ── ẢNH: load ảnh đầu tiên từ server ─────────────────────────
            currentImagePath = "";
            String rawPath = newVal.getImagePath() != null ? newVal.getImagePath() : "";
            // Lấy ảnh đầu tiên nếu lưu dạng "p1|p2|p3"
            String firstPath = rawPath.contains("|") ? rawPath.split("\\|")[0] : rawPath;
            imageHandler.loadFromServer(firstPath);
            // ─────────────────────────────────────────────────────────────
        });
    }

    @FXML
private void onAddProductClick() {
    if (isUploading) {
        showStatus("⏳ Vui lòng chờ upload ảnh xong...", false);
        return;
    }
    if (!validateForm()) return;
    showStatus("Đang gửi...", false);

    LocalDateTime startDT = buildDateTime(
            dpStartDate, txtStartHour, txtStartMinute, txtStartSecond,
            LocalDateTime.now());
    LocalDateTime endDT = buildDateTime(
            dateEnd, txtEndHour, txtEndMinute, txtEndSecond, null);
    if (endDT == null) { showStatus("⚠ Vui lòng chọn ngày kết thúc.", true); return; }

    UserSession session = UserSession.getInstance();
    if (session == null || session.getUserId() == null) {
        showStatus("⚠ Lỗi: Phiên người dùng không hợp lệ!", true); return;
    }

    String payload = gson.toJson(Map.of(
            "sellerId",     session.getUserId(),
            "name",         txtName.getText().trim(),
            "startPrice",   txtStartingPrice.getText().trim(),
            "bidIncrement", txtBidIncrement.getText().trim(),
            "description",  txtDescription.getText().trim(),
            "startTime",    startDT.toString(),
            "endTime",      endDT.toString(),
            "imagePath",    currentImagePath,   // rỗng nếu không chọn ảnh — OK
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
    if (!validateForm()) return;

    LocalDateTime endDT = buildDateTime(
            dateEnd, txtEndHour, txtEndMinute, txtEndSecond,
            selected.getEndTime());

    String imagePath = currentImagePath.isBlank()
            ? (selected.getImagePath() != null ? selected.getImagePath() : "")
            : currentImagePath;

    String payload = gson.toJson(Map.of(
            "productId",    selected.getId(),
            "name",         txtName.getText().trim(),
            "startPrice",   txtStartingPrice.getText().trim(),
            "bidIncrement", txtBidIncrement.getText().trim(),
            "description",  txtDescription.getText().trim(),
            "endTime",      endDT.toString(),
            "imagePath",    imagePath,
            "type",         selectedType
    ));
    client.send(new Message("UPDATE_PRODUCT", payload));
    showStatus("Đang cập nhật...", false);
}

    private void handleServerResponse(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {

                case "MY_PRODUCTS_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        JsonArray arr = null;
                        if (root.has("products") && !root.get("products").isJsonNull())
                            arr = root.getAsJsonArray("products");
                        else if (root.has("items") && !root.get("items").isJsonNull())
                            arr = root.getAsJsonArray("items");
                        else if (root.has("data") && !root.get("data").isJsonNull())
                            arr = root.getAsJsonArray("data");

                        if (arr != null) {
                            List<Item> items = new ArrayList<>();
                            for (JsonElement el : arr) {
                                Item item = deserializeItem(el.getAsJsonObject());
                                if (item != null) items.add(item);
                            }
                            productData.setAll(items);
                            showStatus("Tải " + items.size() + " sản phẩm thành công.", false);
                        } else {
                            showStatus("⚠ " + (root.has("message")
                                    ? root.get("message").getAsString()
                                    : "Không tìm thấy danh sách sản phẩm."), true);
                        }
                    } catch (Exception e) {
                        showStatus("⚠ Lỗi xử lý dữ liệu từ Server!", true);
                    }
                }

                case "ADD_PRODUCT_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        if (root.has("success") && root.get("success").getAsBoolean()) {
                            clearForm();
                            showStatus("✓ Thêm sản phẩm thành công!", false);
                            loadMyProducts();
                        } else {
                            showStatus("⚠ " + safeMsg(root), true);
                        }
                    } catch (Exception e) {
                        showStatus("⚠ Lỗi parse response: " + e.getMessage(), true);
                    }
                }

                case "UPDATE_PRODUCT_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        if (root.has("success") && root.get("success").getAsBoolean()) {
                            loadMyProducts();
                            showStatus("✓ Cập nhật thành công!", false);
                        } else {
                            showStatus("⚠ " + safeMsg(root), true);
                        }
                    } catch (Exception e) {
                        showStatus("⚠ Lỗi: " + e.getMessage(), true);
                    }
                }

                case "DELETE_PRODUCT_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        if (root.has("success") && root.get("success").getAsBoolean()) {
                            Item selected = tableProducts.getSelectionModel().getSelectedItem();
                            if (selected != null) productData.remove(selected);
                            clearForm();
                            showStatus("✓ Đã xoá sản phẩm!", false);
                        } else {
                            showStatus("⚠ " + safeMsg(root), true);
                        }
                    } catch (Exception e) {
                        showStatus("⚠ Lỗi: " + e.getMessage(), true);
                    }
                }

                // ── ẢNH: delegate sang ImageUploadHandler ─────────────────
                case "UPLOAD_IMAGE_RESPONSE" -> imageHandler.onUploadResponse(msg.getPayload());
                case "GET_IMAGE_RESPONSE"    -> imageHandler.onGetImageResponse(msg.getPayload());
                // ─────────────────────────────────────────────────────────
            }
        });
    }

    private boolean validateForm() {
        if (txtName.getText().trim().isEmpty()) {
            showStatus("⚠ Tên sản phẩm không được để trống.", true); return false;
        }
        try {
            Double.parseDouble(txtStartingPrice.getText().trim());
            Double.parseDouble(txtBidIncrement.getText().trim());
        } catch (NumberFormatException e) {
            showStatus("⚠ Giá tiền và bước giá phải là số.", true); return false;
        }
        if (dateEnd.getValue() == null) {
            showStatus("⚠ Vui lòng chọn ngày kết thúc.", true); return false;
        }
        if (dpStartDate.getValue() != null &&
                dpStartDate.getValue().isAfter(dateEnd.getValue())) {
            showStatus("⚠ Ngày bắt đầu phải trước ngày kết thúc.", true); return false;
        }
        if (!isValidTime(txtStartHour, txtStartMinute, txtStartSecond)) {
            showStatus("⚠ Giờ bắt đầu không hợp lệ.", true); return false;
        }
        if (!isValidTime(txtEndHour, txtEndMinute, txtEndSecond)) {
            showStatus("⚠ Giờ kết thúc không hợp lệ.", true); return false;
        }
        return true;
    }

    private boolean isValidTime(TextField h, TextField m, TextField s) {
        try {
            int hour   = h.getText().isEmpty() ? 0 : Integer.parseInt(h.getText());
            int minute = m.getText().isEmpty() ? 0 : Integer.parseInt(m.getText());
            int second = s.getText().isEmpty() ? 0 : Integer.parseInt(s.getText());
            return hour >= 0 && hour <= 23
                    && minute >= 0 && minute <= 59
                    && second >= 0 && second <= 59;
        } catch (NumberFormatException e) { return false; }
    }

    private LocalDateTime buildDateTime(DatePicker dp,
                                         TextField h, TextField m, TextField s,
                                         LocalDateTime fallback) {
        if (dp.getValue() == null) return fallback;
        return dp.getValue().atTime(
                parseTimeField(h, 0), parseTimeField(m, 0), parseTimeField(s, 0));
    }

    private int parseTimeField(TextField field, int def) {
        try {
            String t = field.getText().trim();
            return t.isEmpty() ? def : Integer.parseInt(t);
        } catch (NumberFormatException e) { return def; }
    }

    private void loadMyProducts() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.getUserId() != null)
            client.send(new Message("GET_MY_PRODUCTS",
                    gson.toJson(Map.of("sellerId", session.getUserId()))));
    }

    private void clearForm() {
        txtName.clear();
        txtStartingPrice.clear();
        txtBidIncrement.clear();
        txtDescription.clear();
        dpStartDate.setValue(null);
        txtStartHour.clear(); txtStartMinute.clear(); txtStartSecond.clear();
        dateEnd.setValue(null);
        txtEndHour.clear(); txtEndMinute.clear(); txtEndSecond.clear();
        // ── ẢNH ──
        currentImagePath = "";
        isUploading = false;
        if (imageHandler != null) imageHandler.clearPreview();
        // ─────────
        // Reset type về mặc định
        selectedType = "ART";
        updateTypeButtons("ART");
        tableProducts.getSelectionModel().clearSelection();
    }

    @FXML private void onBackHomeClick(ActionEvent event) {
        try { client.removeListener(listener); } catch (Exception ignored) {}
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    @FXML private void onDeleteClick(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần xoá!", true); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xoá sản phẩm: " + selected.getName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xoá");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                client.send(new Message("DELETE_PRODUCT",
                        gson.toJson(Map.of("productId", selected.getId()))));
                showStatus("Đang xoá...", false);
            }
        });
    }

    private String safeMsg(JsonObject root) {
        return root.has("message")
                ? root.get("message").getAsString() : "Lỗi không xác định";
    }

    private void showStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError
                ? "-fx-text-fill:#e53e3e; -fx-font-size:12px;"
                : "-fx-text-fill:#38a169; -fx-font-size:12px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }
}