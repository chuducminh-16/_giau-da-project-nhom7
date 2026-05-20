module com.auction.client {

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires java.sql;
    requires mysql.connector.j;
    requires com.google.gson;

    opens com.auction.client.controller    to javafx.fxml;
    opens com.auction.shared.network       to com.google.gson;
    opens com.auction.model.Entity.User    to com.google.gson;
    opens com.auction.model.Entity.Auction_Bid to com.google.gson;
    opens com.auction.client.session       to com.google.gson;
    opens com.auction.server.controller    to com.google.gson;

    exports com.auction.client;
    exports com.auction.shared.network;
    exports com.auction.client.session;
    exports com.auction.server.controller;
}