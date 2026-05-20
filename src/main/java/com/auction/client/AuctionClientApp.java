package com.auction.client;

import java.io.IOException;

import com.auction.client.network.NetworkClient;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class AuctionClientApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {

        // ✅ Kết nối server TRƯỚC KHI load bất kỳ màn hình nào
        try {
            NetworkClient.getInstance().connect("localhost", 9090);
            System.out.println("[App] Đã kết nối server.");
        } catch (Exception e) {
            System.err.println("[App] Không thể kết nối server: "
                    + e.getMessage());
            // App vẫn chạy — các Controller sẽ tự xử lý khi send() thất bại
        }

        javafx.geometry.Rectangle2D screen =
                javafx.stage.Screen.getPrimary().getVisualBounds();


        // Load màn hình đầu tiên
        FXMLLoader loader = new FXMLLoader(
            getClass().getResource("/com/auction/client/view/fxml/login-view.fxml"));

        Parent root = loader.load();

        Scene scene = new Scene(root, screen.getWidth(), screen.getHeight());
        stage.setTitle("The Curator - Đăng nhập");
        stage.setScene(scene);
        stage.setX(screen.getMinX());
        stage.setY(screen.getMinY());
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(java.lang.String[] args) {
        launch(args);
    }
}