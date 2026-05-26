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
 

    // ── 📦 MỞ CÁC GÓI CLIENT CHO JAVAFX & GSON ───────────────────────────────
    opens com.auction.client            to javafx.base, com.google.gson;
    opens com.auction.client.controller to javafx.fxml, com.google.gson;
    opens com.auction.client.network    to com.google.gson;
    opens com.auction.client.session    to com.google.gson;
    opens com.auction.client.utils      to javafx.fxml, com.google.gson;
    

    // 🔥 THÊM MỚI: Mở các sub-packages trong folder handler mà bạn vừa chia nhỏ
    // (JavaFX cần quyền đọc nếu có callback UI, Gson cần quyền đọc để bóc dữ liệu payload)
    opens com.auction.client.handler.admin      to javafx.fxml, com.google.gson;
    opens com.auction.client.handler.bidhistory to javafx.fxml, com.google.gson;
    opens com.auction.client.handler.detail     to javafx.fxml, com.google.gson;
    opens com.auction.client.handler.home       to javafx.fxml, com.google.gson;
    opens com.auction.client.handler.livebidding to javafx.fxml, com.google.gson;
    opens com.auction.client.handler.product    to javafx.fxml, com.google.gson;
    opens com.auction.client.handler.profile    to javafx.fxml, com.google.gson;
    

    // ── 📡 MỞ CÁC GÓI SERVER CHO GSON (REFLECTION) ──────────────────────────
    opens com.auction.server.network    to com.google.gson;
    opens com.auction.server.service    to com.google.gson;
    opens com.auction.server.controller to com.google.gson;
    opens com.auction.server.scheduler  to com.google.gson;
    opens com.auction.server.dao.item   to com.google.gson;
    opens com.auction.server.dao.auction to com.google.gson;
    opens com.auction.server.dao.bid    to com.google.gson;
    opens com.auction.server.dao.user   to com.google.gson;
 

    // ── 💎 MỞ CÁC GÓI ENTITY (SHARED MODEL) CHO GSON & JAVAFX PROPERTY ──────
    opens com.auction.shared.model.Entity               to com.google.gson, javafx.base;
    opens com.auction.shared.model.Entity.User          to com.google.gson, javafx.base;
    opens com.auction.shared.model.Entity.Item          to com.google.gson, javafx.base;
    opens com.auction.shared.model.Entity.Auction_Bid   to com.google.gson, javafx.base;
 
    exports com.auction.client;
}