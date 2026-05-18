package com.auction.client.network;

import com.auction.shared.network.Message;

// Interface callback: khi server gửi message về
// Frontend implement cái này để cập nhật UI
public interface MessageListener {

    // Được gọi mỗi khi nhận được message từ server
    void onMessageReceived(Message message);

    // Được gọi khi mất kết nối
    void onDisconnected();
}