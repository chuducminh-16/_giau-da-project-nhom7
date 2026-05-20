module com.auction.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires mysql.connector.j;
<<<<<<< HEAD
//    requires gson;
=======
>>>>>>> c244247a0c42d69833d044c14b081d854c7f8ac1
    requires com.google.gson;
//    requires javafx.web;
    requires java.sql;

    opens com.auction.client.controller to javafx.fxml;
    exports com.auction.client;
}