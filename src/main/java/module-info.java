module com.auction.client {

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires mysql.connector.j;
    requires com.google.gson;
    requires java.sql;

    opens com.auction.client.controller to javafx.fxml, com.google.gson;
    opens com.auction.client.network to com.google.gson;
    opens com.auction.client.model to com.google.gson;
    opens com.auction.client.session to com.google.gson;
    opens com.auction.server.network to com.google.gson;
    opens com.auction.server.service to com.google.gson;
    opens com.auction.shared.model.Entity.User to com.google.gson;
    opens com.auction.shared.model.Entity.Item to com.google.gson;
    opens com.auction.shared.model.Entity.Auction_Bid to com.google.gson;
    
    exports com.auction.client;
    exports com.auction.shared.network;
    exports com.auction.client.session;
    exports com.auction.server.controller;
}