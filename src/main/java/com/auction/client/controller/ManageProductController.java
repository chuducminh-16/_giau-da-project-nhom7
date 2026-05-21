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

    @FXML private TextField  txtName;
    @FXML private TextField  txtStartingPrice;
    @FXML private TextField  txtBidIncrement;
    @FXML private TextArea   txtDescription;
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dateEnd;
    @FXML private ImageView  imgPreview;

    @FXML private TableView<Item>                  tableProducts;
    @FXML private TableColumn<Item, String>        colName;
    @FXML private TableColumn<Item, Double>        colPrice;
    @FXML private TableColumn<Item, LocalDateTime> colTime;
    @FXML private TableColumn<Item, String>        colStatus;
    @FXML private TableColumn<Item, Double>        colCurrentBid;
    @FXML private Label statusLabel;

    private final ObservableList<Item> productData = FXCollections.observableArrayList();
    private String currentImagePath = "";

    private final Gson          gson   = buildGson();
    private final NetworkClient client = NetworkClient.getInstance();
    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    // ── Custom Gson: handle abstract Item + inaccessible fields ──────────
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

            // Dùng Gson đơn giản (không register lại) để tránh đệ quy
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        client.removeListener(listener);
        client.addListener(listener);
        setupTable();
        setupRowClickListener();
        loadMyProducts();
    }

    private void setupTable() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        colCurrentBid.setCellValueFactory(new PropertyValueFactory<>("currentBid"));

        colTime.setCellValueFactory(new PropertyValueFactory<>("endTime"));
        colTime.setCellFactory(col -> new TableCell<>() {
            private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
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
        tableProducts.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (newVal == null) return;
                    txtName.setText(newVal.getName());
                    txtStartingPrice.setText(String.valueOf(newVal.getStartingPrice()));
                    txtBidIncrement.setText(String.valueOf(newVal.getBidIncrement()));

                    String desc = newVal.getDescription();
                    txtDescription.setText(desc != null ? desc : "");

                    if (newVal.getEndTime() != null)
                        dateEnd.setValue(newVal.getEndTime().toLocalDate());
                });
    }

    private void loadMyProducts() {
        String sellerId = UserSession.getInstance().getUserId();
        client.send(new Message("GET_MY_PRODUCTS",
                gson.toJson(Map.of("sellerId", sellerId))));
    }

    @FXML private void onSelectImageClick() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Chọn ảnh sản phẩm");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(imgPreview.getScene().getWindow());
        if (file != null) {
            imgPreview.setImage(new Image(file.toURI().toString()));
            currentImagePath = file.getAbsolutePath();
        }
    }

    @FXML private void onAddProductClick() {
        if (!validateForm()) return;
        showStatus("Đang gửi...", false);

        LocalDateTime startDT = dpStartDate.getValue() != null
                ? dpStartDate.getValue().atTime(8, 0) : LocalDateTime.now();
        LocalDateTime endDT = dateEnd.getValue().atTime(21, 0);

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

    @FXML public void handleUpdateProduct(ActionEvent event) {
        Item selected = tableProducts.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Vui lòng chọn sản phẩm cần sửa!", true); return; }
        if (!validateForm()) return;

        LocalDateTime endDT = dateEnd.getValue() != null
                ? dateEnd.getValue().atTime(21, 0) : selected.getEndTime();

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

    private void handleServerResponse(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {

                case "MY_PRODUCTS_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        if (!root.has("products") || !root.get("products").isJsonArray()) return;

                        JsonArray arr = root.getAsJsonArray("products");
                        List<Item> items = new ArrayList<>();
                        for (JsonElement el : arr) {
                            Item item = deserializeItem(el.getAsJsonObject());
                            if (item != null) items.add(item);
                        }
                        productData.setAll(items);
                    } catch (Exception e) {
                        System.err.println("[ManageProduct] parse MY_PRODUCTS_RESPONSE lỗi: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                case "ADD_PRODUCT_RESPONSE" -> {
                    try {
                        JsonObject root = gson.fromJson(msg.getPayload(), JsonObject.class);
                        boolean success = root.has("success") && root.get("success").getAsBoolean();
                        if (success) {
                            if (root.has("product") && !root.get("product").isJsonNull()) {
                                Item item = deserializeItem(root.getAsJsonObject("product"));
                                if (item != null) productData.add(0, item);
                            }
                            clearForm();
                            showStatus("✓ Thêm sản phẩm thành công!", false);
                            // Reload để chắc chắn hiển thị đúng
                            loadMyProducts();
                        } else {
                            String msg2 = root.has("message") ? root.get("message").getAsString() : "Lỗi không xác định";
                            showStatus("⚠ " + msg2, true);
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
                            String msg2 = root.has("message") ? root.get("message").getAsString() : "Cập nhật thất bại";
                            showStatus("⚠ " + msg2, true);
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
                            String msg2 = root.has("message") ? root.get("message").getAsString() : "Xoá thất bại";
                            showStatus("⚠ " + msg2, true);
                        }
                    } catch (Exception e) {
                        showStatus("⚠ Lỗi: " + e.getMessage(), true);
                    }
                }
            }
        });
    }

    @FXML private void onBackHomeClick(ActionEvent event) {
        client.removeListener(listener);
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
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
        return true;
    }

    private void clearForm() {
        txtName.clear(); txtStartingPrice.clear();
        txtBidIncrement.clear(); txtDescription.clear();
        dpStartDate.setValue(null); dateEnd.setValue(null);
        imgPreview.setImage(null); currentImagePath = "";
        tableProducts.getSelectionModel().clearSelection();
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