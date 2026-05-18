package com.auction.server;

import com.auction.server.network.NetworkServer;

public class Main {
    public static void main(String[] args) {
        // Khởi động server — chặn vòng lặp tại đây
        new NetworkServer().start();
    }
}