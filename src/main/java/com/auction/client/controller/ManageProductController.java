package com.auction.client.controller;

import java.io.File;
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
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;

public class ManageProductController implements Initializable {

    // ── FXML Fields ──
    @FXML private TextField txtName;
    @FXML private TextField txtStartingPrice;
    @FXML private TextField txtBidIncrement;
    @FXML private TextArea txtDescription;
    @FXML private ImageView imgPreview;

    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dateEnd;

    @FXML private TextField txtStartHour;
    @FXML private TextField txtStartMinute;
    @FXML private TextField txtStartSecond;

    @FXML private TextField txtEndHour;
    @FXML private TextField txtEndMinute;
    @FXML private TextField txtEndSecond;

    @FXML private TableView<Item> tableProducts;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, Double> colPrice;
    @FXML private TableColumn<Item, LocalDateTime> colTime;
    @FXML private TableColumn<Item, String> colStatus;
    @FXML private TableColumn<Item, Double> colCurrentBid;

    @FXML private Label statusLabel;

    // ── State & Network ──
    private final ObservableList<Item> productData = FXCollections.observableArrayList();
    private String currentImagePath = "";

    private final Gson gson = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    // ── Logic Gson ──
    private static Gson buildGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Item.class, (JsonDeserializer<Item>) (json, typeOfT, ctx) -> {
                    JsonObject obj = json.getAsJsonObject();
                    return deserializeItem(obj);
                })
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
        } catch (Exception e) {
            System.err.println("[ManageProduct] deserializeItem lỗi: " + e.getMessage());
            return null;
        }
    }

    // ── Khởi tạo ──
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client.removeListener(listener);
        client.addListener(listener);

        setupTable();
        setupRowClickListener();
        setupTimeFieldListeners();
        loadMyProducts();
    }

    // ── UI Logic: Giới hạn số nhập vào ô thời gian ──
    private void setupTimeFieldListeners() {
        limitTimeField(txtStartHour, 23);
        limitTimeField(txtStartMinute, 59);
        limitTimeField(txtStartSecond, 59);
        limitTimeField(txtEndHour, 23);
        limitTimeField(txtEndMinute, 59);
        limitTimeField(txtEndSecond, 59);
    }

    private void limitTimeField(TextField field, int maxValue) {
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

    // ── Table Setup ──
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
                    case "SOLD", "FINISHED"  -> "-fx-text-fill:#e74c3c; -fx-font-weight:bold;";
                    default                  -> "-fx-text-fill:#f1c40f; -fx-font-weight:bold;";
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
        });
    }

    // ── Action Handlers ──
    @FXML
    private void onAddProductClick() {
        if (!validateForm()) return;
        showStatus("Đang gửi...", false);

        LocalDateTime startDT = buildDateTime(dpStartDate, txtStartHour, txtStartMinute, txtStartSecond, LocalDateTime.now());
        LocalDateTime endDT = buildDateTime(dateEnd, txtEndHour, txtEndMinute, txtEndSecond, null);

        if (endDT == null) { showStatus("⚠ Vui lòng chọn ngày kết thúc.", true); return; }

        UserSession session = UserSession.getInstance();
        if (session == null || session.getUserId() == null) {
            showStatus("⚠ Lỗi: Phiên người dùng không hợp lệ!", true);
            return;
        }

        String payload = gson.toJson(Map.of(
                "sellerId",     UserSession.getInstance().getUserId(),
                "name",         txtName.getText().trim(),
                "startPrice",   txtStartingPrice.getText().trim(),
                "bidIncrement", txtBidIncrement.getText().trim(),
                "description",  txtDescription.getText().trim(),
                "startTime",    startDT.toString(),
                "endTime",      endDT.toString(),
                "imagePath",    currentImagePath,
                "status",       "PENDING"
        ));
        client.send(new Message("ADD_PRODUCT", payload));
    }

    @FXML
    public void handleUpdateProduct(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần sửa!", true); return; }
        if (!validateForm()) return;

        LocalDateTime endDT = buildDateTime(dateEnd, txtEndHour, txtEndMinute, txtEndSecond, selected.getEndTime());

        String payload = gson.toJson(Map.of(
                "productId",    selected.getId(),
                "name",         txtName.getText().trim(),
                "startPrice",   txtStartingPrice.getText().trim(),
                "bidIncrement", txtBidIncrement.getText().trim(),
                "description",  txtDescription.getText().trim(),
                "endTime",      endDT.toString()
        ));
        client.send(new Message("UPDATE_PRODUCT", payload));
        showStatus("Đang cập nhật...", false);
    }

    // ── [ĐÃ SỬA] Nhận và phân tích cú pháp gói tin phản hồi từ Server ──
    private void handleServerResponse(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {

                // ✅ FIX: Sửa tận gốc lỗi "Expected a JsonArray but was JsonObject"
                case "MY_PRODUCTS_RESPONSE" -> {
                    try {
                        // 1. Phải đưa về đúng bản chất là JsonObject bọc ngoài của Server trước
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        
                        // 2. Tìm mảng danh sách được bọc trong các từ khóa thuộc tính thông dụng
                        JsonArray arr = null;
                        if (root.has("products") && !root.get("products").isJsonNull()) {
                            arr = root.getAsJsonArray("products");
                        } else if (root.has("items") && !root.get("items").isJsonNull()) {
                            arr = root.getAsJsonArray("items");
                        } else if (root.has("data") && !root.get("data").isJsonNull()) {
                            arr = root.getAsJsonArray("data");
                        }

                        // 3. Nếu tìm thấy mảng, bóc tách và đổ dữ liệu đa hình vào TableView
                        if (arr != null) {
                            List<Item> items = new ArrayList<>();
                            for (JsonElement el : arr) {
                                Item item = deserializeItem(el.getAsJsonObject());
                                if (item != null) items.add(item);
                            }
                            productData.setAll(items);
                            showStatus("Tải " + items.size() + " sản phẩm thành công.", false);
                        } else {
                            // Trường hợp Server gửi dạng Object thông báo không có sản phẩm hoặc lỗi
                            String errorMsg = root.has("message") ? root.get("message").getAsString() : "Không tìm thấy danh sách sản phẩm.";
                            showStatus("⚠ " + errorMsg, true);
                        }
                    } catch (Exception e) {
                        System.err.println("[ManageProduct] parse MY_PRODUCTS_RESPONSE lỗi: " + e.getMessage());
                        e.printStackTrace();
                        showStatus("⚠ Lỗi xử lý cấu trúc dữ liệu từ Server!", true);
                    }
                }

                case "ADD_PRODUCT_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        boolean success = root.has("success") && root.get("success").getAsBoolean();
                        if (success) {
                            if (root.has("item") && !root.get("item").isJsonNull()) {
                                Item item = deserializeItem(root.getAsJsonObject("item"));
                                if (item != null) productData.add(0, item);
                            }
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
                        boolean success = root.has("success") && root.get("success").getAsBoolean();
                        if (success) {
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
                        boolean success = root.has("success") && root.get("success").getAsBoolean();
                        if (success) {
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
            }
        });
    }

    // ── Validate ──
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
                parseTimeField(h, 0),
                parseTimeField(m, 0),
                parseTimeField(s, 0));
    }

    private int parseTimeField(TextField field, int def) {
        try {
            String t = field.getText().trim();
            return t.isEmpty() ? def : Integer.parseInt(t);
        } catch (NumberFormatException e) { return def; }
    }

    private void loadMyProducts() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.getUserId() != null) {
            String sellerId = session.getUserId();
            client.send(new Message("GET_MY_PRODUCTS", gson.toJson(Map.of("sellerId", sellerId))));
        }
    }

    // ── Clear form ──
    private void clearForm() {
        txtName.clear(); txtStartingPrice.clear();
        txtBidIncrement.clear(); txtDescription.clear();
        dpStartDate.setValue(null);
        txtStartHour.clear(); txtStartMinute.clear(); txtStartSecond.clear();
        dateEnd.setValue(null);
        txtEndHour.clear(); txtEndMinute.clear(); txtEndSecond.clear();
        imgPreview.setImage(null);
        currentImagePath = "";
        tableProducts.getSelectionModel().clearSelection();
    }

    @FXML private void onBackHomeClick(ActionEvent event) {
        try {
            client.removeListener(listener);
        } catch (Exception e) {
            System.err.println("Lỗi khi xóa listener: " + e.getMessage());
        }
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // ── DELETE ──
    @FXML
    private void onDeleteClick(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Vui lòng chọn sản phẩm cần xoá!", true); return;
        }
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

    // ── Chọn ảnh ──
    @FXML
    private void onSelectImageClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(imgPreview.getScene().getWindow());
        if (file != null) {
            imgPreview.setImage(new Image(file.toURI().toString()));
            // Lưu tên file hoặc một cấu trúc tương đối để phục vụ đóng gói đồng bộ về sau thay vì đường dẫn tuyệt đối máy cá nhân
            currentImagePath = file.getAbsolutePath();
        }
    }

    // ── Helpers ──
    private String safeMsg(JsonObject root) {
        return root.has("message") ? root.get("message").getAsString() : "Lỗi không xác định";
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