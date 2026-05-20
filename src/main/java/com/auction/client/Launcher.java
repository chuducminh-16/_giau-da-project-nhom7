package com.auction.client;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        System.setProperty("prism.allowhidpi", "true");
        Application.launch(AuctionClientApp.class, args);
    }
}
