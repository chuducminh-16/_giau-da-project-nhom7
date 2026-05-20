module com.auction.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires mysql.connector.j;
    requires com.google.gson;
//    requires javafx.web;
    requires java.sql;

    opens com.auction.client.controller to javafx.fxml;
    exports com.auction.client;
}