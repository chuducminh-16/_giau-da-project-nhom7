package com.auction.client.controller;

import com.auction.client.SceneEngine;
import com.auction.client.model.Product;
import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;
import javafx.application.Platform;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.ResourceBundle;

public class ManageProductController implements Initializable {

    // ── FXML fields ────────────────────────────────────
    @FXML private TextField  txtName;
    @FXML private TextField  txtStartingPrice;
    @FXML private TextField  txtBidIncrement;
    @FXML private TextArea   txtDescription;
    @FXML private ImageView  imgPreview;

    // DatePicker chọn ngày
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dateEnd;

    // TextField chọn giờ:phút:giây bắt đầu
    @FXML private TextField txtStartHour;
    @FXML private TextField txtStartMinute;
    @FXML private TextField txtStartSecond;

    // TextField chọn giờ:phút:giây kết thúc
    @FXML private TextField txtEndHour;
    @FXML private TextField txtEndMinute;
    @FXML private TextField txtEndSecond;

    // Bảng sản phẩm
    @FXML private TableView<Product>              tableProducts;
    @FXML private TableColumn<Product, String>    colName;
    @FXML private TableColumn<Product, Double>    colPrice;
    @FXML private TableColumn<Product, LocalDateTime> colTime;
    @FXML private TableColumn<Product, String>    colStatus;
    @FXML private TableColumn<Product, Double>    colCurrentBid;

    @FXML private Label statusLabel;

    // ── State ──────────────────────────────────────────
    private final ObservableList<Product> productData =
            FXCollections.observableArrayList();
    private String currentImagePath = "";

    // ── Tools ──────────────────────────────────────────
    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    private final NetworkClient.MessageListener listener =
            this::handleServerResponse;

    // ── initialize ─────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupRowClickListener();
        setupTimeFieldListeners(); // ← giới hạn nhập giờ/phút/giây
        client.addListener(listener);
        loadMyProducts();
    }

    // ── Giới hạn nhập cho các ô giờ/phút/giây ─────────
    private void setupTimeFieldListeners() {
        // Chỉ cho nhập số, tối đa 2 ký tự
        limitTimeField(txtStartHour,   23);
        limitTimeField(txtStartMinute, 59);
        limitTimeField(txtStartSecond, 59);
        limitTimeField(txtEndHour,     23);
        limitTimeField(txtEndMinute,   59);
        limitTimeField(txtEndSecond,   59);
    }

    /**
     * Giới hạn TextField chỉ nhận số, tối đa 2 ký tự, không vượt maxValue.
     */
    private void limitTimeField(TextField field, int maxValue) {
        field.textProperty().addListener((obs, oldVal, newVal) -> {
            // Chỉ cho nhập số
            if (!newVal.matches("\\d*")) {
                field.setText(newVal.replaceAll("[^\\d]", ""));
                return;
            }
            // Tối đa 2 ký tự
            if (newVal.length() > 2) {
                field.setText(oldVal);
                return;
            }
            // Không vượt maxValue
            if (!newVal.isEmpty()) {
                try {
                    int val = Integer.parseInt(newVal);
                    if (val > maxValue) {
                        field.setText(String.valueOf(maxValue));
                    }
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    // ── Setup bảng ─────────────────────────────────────
    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));

        // Format thời gian kết thúc — hiện đến giây
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

        // Màu cột Status
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); return; }
                setText(item);
                setStyle(switch (item) {
                    case "ACTIVE"  -> "-fx-text-fill:#2ecc71; -fx-font-weight:bold;";
                    case "SOLD"    -> "-fx-text-fill:#e74c3c; -fx-font-weight:bold;";
                    default        -> "-fx-text-fill:#f1c40f; -fx-font-weight:bold;";
                });
            }
        });

        tableProducts.setItems(productData);
    }

    // Click vào hàng → đổ dữ liệu lên form
    private void setupRowClickListener() {
        tableProducts.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal == null) return;
                    txtName.setText(newVal.getName());
                    txtStartingPrice.setText(String.valueOf(newVal.getStartingPrice()));
                    txtBidIncrement.setText(String.valueOf(newVal.getBidIncrement()));
                    txtDescription.setText(newVal.getDescription());

                    // Đổ ngày + giờ bắt đầu
                    if (newVal.getStartTime() != null) {
                        dpStartDate.setValue(newVal.getStartTime().toLocalDate());
                        txtStartHour.setText(
                                String.format("%02d", newVal.getStartTime().getHour()));
                        txtStartMinute.setText(
                                String.format("%02d", newVal.getStartTime().getMinute()));
                        txtStartSecond.setText(
                                String.format("%02d", newVal.getStartTime().getSecond()));
                    }

                    // Đổ ngày + giờ kết thúc
                    if (newVal.getEndTime() != null) {
                        dateEnd.setValue(newVal.getEndTime().toLocalDate());
                        txtEndHour.setText(
                                String.format("%02d", newVal.getEndTime().getHour()));
                        txtEndMinute.setText(
                                String.format("%02d", newVal.getEndTime().getMinute()));
                        txtEndSecond.setText(
                                String.format("%02d", newVal.getEndTime().getSecond()));
                    }
                });
    }

    // ── Load sản phẩm từ server ─────────────────────────
    private void loadMyProducts() {
        String sellerId = UserSession.getInstance().getUserId();
        client.send(new Message("GET_MY_PRODUCTS",
                gson.toJson(Map.of("sellerId", sellerId))));
    }

    // ── Chọn ảnh ───────────────────────────────────────
    @FXML
    private void onSelectImageClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(imgPreview.getScene().getWindow());
        if (file != null) {
            imgPreview.setImage(new Image(file.toURI().toString()));
            currentImagePath = file.getAbsolutePath();
        }
    }

    // ── ADD: Thêm sản phẩm mới ─────────────────────────
    @FXML
    private void onAddProductClick() {
        if (!validateForm()) return;

        LocalDateTime startDT = buildDateTime(
                dpStartDate, txtStartHour, txtStartMinute, txtStartSecond,
                LocalDateTime.now()); // fallback = now

        LocalDateTime endDT = buildDateTime(
                dateEnd, txtEndHour, txtEndMinute, txtEndSecond,
                null);

        if (endDT == null) {
            showStatus("⚠ Vui lòng chọn ngày kết thúc.", true);
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
        showStatus("Đang gửi...", false);
    }

    // ── UPDATE: Sửa sản phẩm đang chọn ────────────────
    @FXML
    public void handleUpdateProduct(ActionEvent event) {
        Product selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Vui lòng chọn sản phẩm cần sửa!", true);
            return;
        }
        if (!validateForm()) return;

        LocalDateTime endDT = buildDateTime(
                dateEnd, txtEndHour, txtEndMinute, txtEndSecond,
                selected.getEndTime()); // fallback = endTime cũ

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

    // ── DELETE: Xoá sản phẩm đang chọn ────────────────
    @FXML
    private void onDeleteClick(ActionEvent event) {
        Product selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showStatus("Vui lòng chọn sản phẩm cần xoá!", true);
            return;
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

    // ── Nhận phản hồi từ server ────────────────────────
    private void handleServerResponse(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {

                case "MY_PRODUCTS_RESPONSE" -> {
                    ProductListResponse resp = gson.fromJson(
                            msg.getPayload(), ProductListResponse.class);
                    if (resp.products != null)
                        productData.setAll(resp.products);
                }

                case "ADD_PRODUCT_RESPONSE" -> {
                    ProductResponse resp = gson.fromJson(
                            msg.getPayload(), ProductResponse.class);
                    if (resp.success) {
                        if (resp.product != null)
                            productData.add(0, resp.product);
                        clearForm();
                        showStatus("✓ Thêm sản phẩm thành công!", false);
                    } else {
                        showStatus("⚠ " + resp.message, true);
                    }
                }

                case "UPDATE_PRODUCT_RESPONSE" -> {
                    ProductResponse resp = gson.fromJson(
                            msg.getPayload(), ProductResponse.class);
                    if (resp.success) {
                        tableProducts.refresh();
                        showStatus("✓ Cập nhật thành công!", false);
                    } else {
                        showStatus("⚠ " + resp.message, true);
                    }
                }

                case "DELETE_PRODUCT_RESPONSE" -> {
                    ProductResponse resp = gson.fromJson(
                            msg.getPayload(), ProductResponse.class);
                    if (resp.success) {
                        Product selected = tableProducts
                                .getSelectionModel().getSelectedItem();
                        if (selected != null) productData.remove(selected);
                        clearForm();
                        showStatus("✓ Đã xoá sản phẩm!", false);
                    } else {
                        showStatus("⚠ " + resp.message, true);
                    }
                }
            }
        });
    }

    // ── Back Home ──────────────────────────────────────
    @FXML
    private void onBackHomeClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    // ── Validate form ──────────────────────────────────
    private boolean validateForm() {
        if (txtName.getText().trim().isEmpty()) {
            showStatus("⚠ Tên sản phẩm không được để trống.", true);
            return false;
        }
        try {
            Double.parseDouble(txtStartingPrice.getText().trim());
            Double.parseDouble(txtBidIncrement.getText().trim());
        } catch (NumberFormatException e) {
            showStatus("⚠ Giá tiền và bước giá phải là số.", true);
            return false;
        }
        if (dateEnd.getValue() == null) {
            showStatus("⚠ Vui lòng chọn ngày kết thúc.", true);
            return false;
        }
        if (dpStartDate.getValue() != null &&
                dpStartDate.getValue().isAfter(dateEnd.getValue())) {
            showStatus("⚠ Ngày bắt đầu phải trước ngày kết thúc.", true);
            return false;
        }
        // Validate giờ/phút/giây
        if (!validateTimeFields()) return false;

        return true;
    }

    private boolean validateTimeFields() {
        // Nếu đã nhập ngày thì phải nhập giờ hợp lệ
        if (dpStartDate.getValue() != null) {
            if (!isValidTime(txtStartHour, txtStartMinute, txtStartSecond)) {
                showStatus("⚠ Giờ bắt đầu không hợp lệ (HH:MM:SS).", true);
                return false;
            }
        }
        if (dateEnd.getValue() != null) {
            if (!isValidTime(txtEndHour, txtEndMinute, txtEndSecond)) {
                showStatus("⚠ Giờ kết thúc không hợp lệ (HH:MM:SS).", true);
                return false;
            }
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
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ── Build LocalDateTime từ DatePicker + 3 TextField ─
    /**
     * Ghép DatePicker + txtHour + txtMinute + txtSecond thành LocalDateTime.
     * Nếu ô nào để trống thì dùng 0.
     * Nếu datePicker null thì trả về fallback.
     */
    private LocalDateTime buildDateTime(DatePicker datePicker,
                                        TextField hField,
                                        TextField mField,
                                        TextField sField,
                                        LocalDateTime fallback) {
        if (datePicker.getValue() == null) return fallback;

        int h = parseTimeField(hField, 0);
        int m = parseTimeField(mField, 0);
        int s = parseTimeField(sField, 0);

        return datePicker.getValue().atTime(h, m, s);
    }

    private int parseTimeField(TextField field, int defaultVal) {
        try {
            String text = field.getText().trim();
            return text.isEmpty() ? defaultVal : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    // ── Helpers ────────────────────────────────────────
    private void clearForm() {
        txtName.clear();
        txtStartingPrice.clear();
        txtBidIncrement.clear();
        txtDescription.clear();

        dpStartDate.setValue(null);
        txtStartHour.clear();
        txtStartMinute.clear();
        txtStartSecond.clear();

        dateEnd.setValue(null);
        txtEndHour.clear();
        txtEndMinute.clear();
        txtEndSecond.clear();

        imgPreview.setImage(null);
        currentImagePath = "";
        tableProducts.getSelectionModel().clearSelection();
    }

    private void showStatus(String msg, boolean isError) {
        if (statusLabel == null) return;
        statusLabel.setText(msg);
        statusLabel.setStyle(isError
                ? "-fx-text-fill: #e53e3e; -fx-font-size: 12px;"
                : "-fx-text-fill: #38a169; -fx-font-size: 12px;");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    // ── DTOs ───────────────────────────────────────────
    private record ProductResponse(
            boolean success, String message, Product product) {}
    private record ProductListResponse(
            boolean success, java.util.List<Product> products) {}
}
