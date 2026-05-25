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

    @FXML private TextField txtName;
    @FXML private TextField txtStartingPrice;
    @FXML private TextField txtBidIncrement;
    @FXML private TextArea  txtDescription;

    // FIX: 1 ảnh chính và 3 ảnh phụ
    @FXML private ImageView imgPreview1;
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

    @FXML private TableView<Item>               tableProducts;
    @FXML private TableColumn<Item, String>     colName;
    @FXML private TableColumn<Item, Double>     colPrice;
    @FXML private TableColumn<Item, LocalDateTime> colTime;
    @FXML private TableColumn<Item, String>     colStatus;
    @FXML private TableColumn<Item, Double>     colCurrentBid;

    @FXML private Label statusLabel;

    private final ObservableList<Item> productData = FXCollections.observableArrayList();

    // FIX: 3 đường dẫn ảnh, "" = không thay đổi
    private String imagePath1 = "";
    private String imagePath2 = "";
    private String imagePath3 = "";
    private String imagePath4 = "";

    private final Gson gson = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    // ── Tách đường dẫn ảnh từ chuỗi "path1|path2|path3" ─────────────────
    private static String[] splitImagePaths(String raw) {
        if (raw == null || raw.isBlank()) return new String[]{"", "", ""};
        String[] parts = raw.split("\\|", -1);
        String p1 = parts.length > 0 ? parts[0] : "";
        String p2 = parts.length > 1 ? parts[1] : "";
        String p3 = parts.length > 2 ? parts[2] : "";
        String p4 = parts.length > 3 ? parts[3] : "";
        return new String[]{p1, p2, p3, p4};
    }

    // ── Ghép 3 đường dẫn thành "path1|path2|path3" ───────────────────────
    private static String joinImagePaths(String p1, String p2, String p3, String p4) {
        // Loại bỏ dấu | thừa ở cuối nếu ảnh 2,3 trống
        StringBuilder sb = new StringBuilder(p1 != null ? p1 : "");
        sb.append("|").append(p2 != null ? p2 : "");
        sb.append("|").append(p3 != null ? p3 : "");
        sb.append("|").append(p4 != null ? p4 : "");
        // Trim trailing pipes
        String result = sb.toString();
        while (result.endsWith("|")) result = result.substring(0, result.length() - 1);
        return result;
    }

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
            return null;
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client.removeListener(listener);
        client.addListener(listener);
        setupTable();
        setupRowClickListener();
        setupTimeFieldListeners();
        loadMyProducts();
    }

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

            // Load ảnh hiện tại vào 4 preview, reset đường dẫn mới về ""
            imagePath1 = "";
            imagePath2 = "";
            imagePath3 = "";
            imagePath4 = "";
            String[] paths = splitImagePaths(newVal.getImagePath());
            loadPreview(imgPreview1, paths[0]);
            loadPreview(imgPreview2, paths[1]);
            loadPreview(imgPreview3, paths[2]);
            loadPreview(imgPreview4, paths.length > 3 ? paths[3] : "");
        });
    }

    // ── Load ảnh vào ImageView, hỗ trợ jpg/png/jfif/webp/bmp/gif ────────
    private void loadPreview(ImageView iv, String path) {
        if (iv == null) return;
        if (path == null || path.isBlank()) { iv.setImage(null); return; }
        try {
            File f = new File(path);
            if (!f.exists()) { iv.setImage(null); return; }

            String lower = path.toLowerCase();
            // JavaFX Image hỗ trợ trực tiếp: jpg, jpeg, png, bmp, gif
            // jfif thực chất là JPEG — đổi extension rồi đọc qua ImageIO
            // webp cần ImageIO đọc rồi convert sang WritableImage
            if (lower.endsWith(".webp") || lower.endsWith(".jfif")) {
                java.awt.image.BufferedImage bi =
                        javax.imageio.ImageIO.read(f);
                if (bi == null) { iv.setImage(null); return; }
                javafx.scene.image.WritableImage wi =
                        new javafx.scene.image.WritableImage(bi.getWidth(), bi.getHeight());
                javafx.embed.swing.SwingFXUtils.toFXImage(bi, wi);
                iv.setImage(wi);
            } else {
                iv.setImage(new Image(f.toURI().toString(), true));
            }
        } catch (Exception e) {
            System.err.println("[ManageProduct] Lỗi load ảnh: " + e.getMessage());
            iv.setImage(null);
        }
    }

    // ── Nút chọn ảnh 1 / 2 / 3 / 4 ──────────────────────────────────────────
    @FXML private void onSelectImage1Click() { pickImage(1); }
    @FXML private void onSelectImage2Click() { pickImage(2); }
    @FXML private void onSelectImage3Click() { pickImage(3); }
    @FXML private void onSelectImage4Click() { pickImage(4); }

    private void pickImage(int slot) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh " + slot);
        // Tất cả định dạng ảnh phổ biến
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Tất cả ảnh",
                        "*.png", "*.jpg", "*.jpeg", "*.jfif",
                        "*.webp", "*.bmp", "*.gif", "*.tif", "*.tiff"),
                new FileChooser.ExtensionFilter("JPG / JPEG / JFIF",
                        "*.jpg", "*.jpeg", "*.jfif"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("WebP", "*.webp"),
                new FileChooser.ExtensionFilter("BMP / GIF / TIFF",
                        "*.bmp", "*.gif", "*.tif", "*.tiff")
        );
        // Lấy window từ một ImageView bất kỳ
        File file = chooser.showOpenDialog(imgPreview1.getScene().getWindow());
        if (file == null) return;
        String path = file.getAbsolutePath();
        switch (slot) {
            case 1 -> { imagePath1 = path; loadPreview(imgPreview1, path); }
            case 2 -> { imagePath2 = path; loadPreview(imgPreview2, path); }
            case 3 -> { imagePath3 = path; loadPreview(imgPreview3, path); }
            case 4 -> { imagePath4 = path; loadPreview(imgPreview4, path); }
        }
    }

    // ── Tạo imagePath cuối cùng để gửi server ───────────────────────────
    // Nếu slot nào trống ("") → giữ giá trị cũ từ item đang chọn (nếu có)
    private String buildFinalImagePath(Item selected) {
        String[] existing = selected != null
                ? splitImagePaths(selected.getImagePath())
                : new String[]{"", "", ""};

        String p1 = imagePath1.isBlank() ? existing[0] : imagePath1;
        String p2 = imagePath2.isBlank() ? existing[1] : imagePath2;
        String p3 = imagePath3.isBlank() ? existing[2] : imagePath3;
        String p4 = imagePath4.isBlank() ? existing[3] : imagePath4;

        return joinImagePaths(p1, p2, p3, p4);
    }

    @FXML
    private void onAddProductClick() {
        if (!validateForm()) return;
        showStatus("Đang gửi...", false);

        LocalDateTime startDT = buildDateTime(dpStartDate, txtStartHour, txtStartMinute, txtStartSecond, LocalDateTime.now());
        LocalDateTime endDT   = buildDateTime(dateEnd, txtEndHour, txtEndMinute, txtEndSecond, null);
        if (endDT == null) { showStatus("⚠ Vui lòng chọn ngày kết thúc.", true); return; }

        UserSession session = UserSession.getInstance();
        if (session == null || session.getUserId() == null) {
            showStatus("⚠ Lỗi: Phiên người dùng không hợp lệ!", true); return;
        }

        // Khi thêm mới, ghép 3 đường dẫn
        String imagePath = joinImagePaths(imagePath1, imagePath2, imagePath3, imagePath4);

        String payload = gson.toJson(Map.of(
                "sellerId",     session.getUserId(),
                "name",         txtName.getText().trim(),
                "startPrice",   txtStartingPrice.getText().trim(),
                "bidIncrement", txtBidIncrement.getText().trim(),
                "description",  txtDescription.getText().trim(),
                "startTime",    startDT.toString(),
                "endTime",      endDT.toString(),
                "imagePath",    imagePath,
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

        // Giữ ảnh cũ ở slot nào chưa chọn mới
        String imagePath = buildFinalImagePath(selected);

        String payload = gson.toJson(Map.of(
                "productId",    selected.getId(),
                "name",         txtName.getText().trim(),
                "startPrice",   txtStartingPrice.getText().trim(),
                "bidIncrement", txtBidIncrement.getText().trim(),
                "description",  txtDescription.getText().trim(),
                "endTime",      endDT.toString(),
                "imagePath",    imagePath
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
                            String errorMsg = root.has("message")
                                    ? root.get("message").getAsString()
                                    : "Không tìm thấy danh sách sản phẩm.";
                            showStatus("⚠ " + errorMsg, true);
                        }
                    } catch (Exception e) {
                        showStatus("⚠ Lỗi xử lý dữ liệu từ Server!", true);
                    }
                }

                case "ADD_PRODUCT_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        boolean success = root.has("success") && root.get("success").getAsBoolean();
                        if (success) {
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
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59 && second >= 0 && second <= 59;
        } catch (NumberFormatException e) { return false; }
    }

    private LocalDateTime buildDateTime(DatePicker dp, TextField h, TextField m, TextField s, LocalDateTime fallback) {
        if (dp.getValue() == null) return fallback;
        return dp.getValue().atTime(parseTimeField(h, 0), parseTimeField(m, 0), parseTimeField(s, 0));
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
            client.send(new Message("GET_MY_PRODUCTS",
                    gson.toJson(Map.of("sellerId", session.getUserId()))));
        }
    }

    private void clearForm() {
        txtName.clear(); txtStartingPrice.clear();
        txtBidIncrement.clear(); txtDescription.clear();
        dpStartDate.setValue(null);
        txtStartHour.clear(); txtStartMinute.clear(); txtStartSecond.clear();
        dateEnd.setValue(null);
        txtEndHour.clear(); txtEndMinute.clear(); txtEndSecond.clear();
        if (imgPreview1 != null) imgPreview1.setImage(null);
        if (imgPreview2 != null) imgPreview2.setImage(null);
        if (imgPreview3 != null) imgPreview3.setImage(null);
        if (imgPreview4 != null) imgPreview4.setImage(null);
        imagePath1 = ""; imagePath2 = ""; imagePath3 = ""; imagePath4 = "";
        tableProducts.getSelectionModel().clearSelection();
    }

    @FXML private void onBackHomeClick(ActionEvent event) {
        try { client.removeListener(listener); } catch (Exception ignored) {}
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    @FXML
    private void onDeleteClick(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần xoá!", true); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Xoá sản phẩm: " + selected.getName() + "?", ButtonType.YES, ButtonType.NO);
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
