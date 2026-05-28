package com.auction.client.controller;

import java.util.Map;

import com.auction.client.network.Message;
import com.auction.client.network.NetworkClient;
import com.auction.client.session.UserSession;
import com.google.gson.Gson;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * WalletController (Client) — điều khiển popup nạp tiền.
 * File hoàn toàn mới, không can thiệp vào các controller cũ.
 */
public class WalletController {

    @FXML private Label  lblBalance;    // Hiển thị số dư hiện tại
    @FXML private TextField txtAmount; // Ô nhập số tiền muốn nạp
    @FXML private Button btnTopUp;     // Nút nạp tiền
    @FXML private Label  lblStatus;    // Thông báo kết quả

    private final Gson          gson   = new Gson();
    private final NetworkClient client = NetworkClient.getInstance();

    // Listener nhận phản hồi từ server — lưu lại để có thể gỡ khi đóng
    private final NetworkClient.MessageListener listener = this::handleServerResponse;

    @FXML
    public void initialize() {
        client.addListener(listener);
        loadBalance(); // Lấy số dư hiện tại khi mở popup
    }

    /** Lấy số dư từ server khi mở popup */
    private void loadBalance() {
        String userId = UserSession.getInstance().getUserId();
        if (userId == null) return;
        client.send(new Message("GET_BALANCE",
                gson.toJson(Map.of("userId", userId))));
    }

    /** Xử lý click nút Nạp tiền */
    @FXML
    public void onTopUpClick() {
        String input = txtAmount.getText().trim().replaceAll("[^\\d]", "");
        if (input.isEmpty()) {
            showStatus("⚠ Vui lòng nhập số tiền.", true);
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            showStatus("⚠ Số tiền không hợp lệ.", true);
            return;
        }

        if (amount <= 0) {
            showStatus("⚠ Số tiền phải lớn hơn 0.", true);
            return;
        }

        String userId = UserSession.getInstance().getUserId();
        client.send(new Message("TOP_UP",
                gson.toJson(Map.of("userId", userId, "amount", amount))));
        btnTopUp.setDisable(true);
        btnTopUp.setText("Đang nạp...");
    }

    /** Xử lý phản hồi từ server */
    private void handleServerResponse(Message msg) {
        switch (msg.getType()) {

            case "BALANCE_RESPONSE" -> {
                BalanceDto dto = gson.fromJson(msg.getPayload(), BalanceDto.class);
                Platform.runLater(() -> {
                    if (lblBalance != null)
                        lblBalance.setText(String.format("Số dư: %,.0f VND", dto.balance()));
                });
            }

            case "TOP_UP_RESPONSE" -> {
                TopUpResponse dto = gson.fromJson(msg.getPayload(), TopUpResponse.class);
                Platform.runLater(() -> {
                    btnTopUp.setDisable(false);
                    btnTopUp.setText("Nạp tiền");
                    if (dto.success()) {
                        txtAmount.clear();
                        showStatus("✓ " + dto.message(), false);
                        if (lblBalance != null)
                            lblBalance.setText(String.format("Số dư: %,.0f VND", dto.newBalance()));
                    } else {
                        showStatus("⚠ " + dto.message(), true);
                    }
                });
            }
        }
    }

    /** Đóng popup */
    @FXML
    public void onCloseClick() {
        client.removeListener(listener);
        Stage stage = (Stage) btnTopUp.getScene().getWindow();
        stage.close();
    }

    private void showStatus(String msg, boolean isError) {
        if (lblStatus == null) return;
        lblStatus.setText(msg);
        lblStatus.setStyle(isError
                ? "-fx-text-fill: #e53e3e; -fx-font-size: 12px;"
                : "-fx-text-fill: #38a169; -fx-font-size: 12px;");
        lblStatus.setVisible(true);
        lblStatus.setManaged(true);
    }

    private record BalanceDto(boolean success, double balance) {}
    private record TopUpResponse(boolean success, String message, double newBalance) {}
}
