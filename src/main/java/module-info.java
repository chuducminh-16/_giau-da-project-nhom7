module com.auction.client {

    // ── JavaFX ────────────────────────────────────────────
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;


    // ── Database ──────────────────────────────────────────
    requires java.sql;
    requires mysql.connector.j;

    // ── thêm Gson ───────────────────────────────────
    requires com.google.gson;

    // ── Mở Controller cho JavaFX FXML reflection ─────────
    // FXML cần reflection để inject @FXML field và gọi handler
    opens com.auction.client.controller to javafx.fxml;

    // ── mở thêm các package dùng Gson ───────────────
    // Gson dùng reflection để serialize/deserialize object.
    // Nếu không opens, Gson sẽ ném IllegalAccessException lúc runtime
    // ngay cả khi compile không báo lỗi.
    opens com.auction.shared.network to com.google.gson;
    opens com.auction.shared.model.Entity.User to com.google.gson;
    opens com.auction.shared.model.Entity.Auction_Bid to com.google.gson;
    opens com.auction.shared.model.Entity.Item   to com.google.gson;

    // ← THÊM: Gson cần reflect vào session và controller package
    opens com.auction.client.session       to com.google.gson;
    opens com.auction.server.controller    to com.google.gson;

    // ── Export package để các module khác nhìn thấy ───────
    exports com.auction.client;


    // export shared.network để server module dùng được Message.java
    // (nếu client và server là 2 module riêng trong cùng 1 project)
    exports com.auction.shared.network;

     // ← THÊM: export controller để các phần khác dùng
    exports com.auction.server.controller;
    exports com.auction.client.session;
}