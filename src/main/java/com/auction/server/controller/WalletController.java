package com.auction.server.controller;

import com.auction.client.network.Message;
import com.auction.server.service.WalletService;
import com.google.gson.Gson;

import java.util.Map;

/**
 * WalletController — xử lý các request liên quan đến ví tiền.
 * File hoàn toàn mới, không can thiệp vào các controller cũ.
 * Được gọi từ ClientHandler khi nhận message type: TOP_UP, GET_BALANCE
 */
public class WalletController {

    private final WalletService walletService = new WalletService();
    private final Gson gson = new Gson();

    /**
     * Xử lý nạp tiền.
     * Payload: {"userId": "...", "amount": 100000}
     */
    public Message handleTopUp(String payload) {
        try {
            TopUpDto dto = gson.fromJson(payload, TopUpDto.class);
            double newBalance = walletService.topUp(dto.userId(), dto.amount());

            if (newBalance >= 0) {
                return new Message("TOP_UP_RESPONSE", gson.toJson(Map.of(
                        "success",    true,
                        "newBalance", newBalance,
                        "message",    String.format("Nạp %.0f VND thành công!", dto.amount())
                )));
            } else {
                return new Message("TOP_UP_RESPONSE", gson.toJson(Map.of(
                        "success", false,
                        "message", "Nạp tiền thất bại."
                )));
            }
        } catch (Exception e) {
            return new Message("TOP_UP_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "message", "Lỗi: " + e.getMessage()
            )));
        }
    }

    /**
     * Lấy số dư hiện tại.
     * Payload: {"userId": "..."}
     */
    public Message handleGetBalance(String payload) {
        try {
            GetBalanceDto dto = gson.fromJson(payload, GetBalanceDto.class);
            double balance = walletService.getBalance(dto.userId());
            return new Message("BALANCE_RESPONSE", gson.toJson(Map.of(
                    "success", true,
                    "balance", balance
            )));
        } catch (Exception e) {
            return new Message("BALANCE_RESPONSE", gson.toJson(Map.of(
                    "success", false,
                    "balance", 0.0
            )));
        }
    }

    private record TopUpDto(String userId, double amount) {}
    private record GetBalanceDto(String userId) {}
}
