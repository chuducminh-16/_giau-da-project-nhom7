module com.auction.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    opens com.auction.client to javafx.fxml;
    exports com.auction.client;
}