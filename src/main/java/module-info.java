module com.auction.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires javafx.swing;          // FIX: thêm để dùng SwingFXUtils cho webp/jfif
    requires java.desktop;          // FIX: thêm để dùng javax.imageio.ImageIO
    requires mysql.connector.j;
    requires com.google.gson;
    requires java.sql;
 
    // ── Mở cho JavaFX ─────────────────────────────────────────────────────
    opens com.auction.client.controller to javafx.fxml, com.google.gson;
    opens com.auction.client.network    to com.google.gson;
    opens com.auction.client.session    to com.google.gson;
 
    // ── Mở cho Gson (server) ──────────────────────────────────────────────
    opens com.auction.server.network     to com.google.gson;
    opens com.auction.server.service     to com.google.gson;
    opens com.auction.server.controller  to com.google.gson;
    opens com.auction.server.scheduler   to com.google.gson;
    opens com.auction.server.dao.item    to com.google.gson;
    opens com.auction.server.dao.auction to com.google.gson;
    opens com.auction.server.dao.bid     to com.google.gson;
    opens com.auction.server.dao.user    to com.google.gson;
 
    // ── Mở Entity cho Gson ────────────────────────────────────────────────
    opens com.auction.shared.model.Entity               to com.google.gson, javafx.base;
    opens com.auction.shared.model.Entity.User          to com.google.gson, javafx.base;
    opens com.auction.shared.model.Entity.Item          to com.google.gson, javafx.base;
    opens com.auction.shared.model.Entity.Auction_Bid   to com.google.gson, javafx.base;
 
    exports com.auction.client;
}