package com.auction.client.utils;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class NavigationUtils {
    public static void switchScene(ActionEvent event, String fxmlFile, String title) throws IOException {
        // 1. Load file FXML mới
        FXMLLoader loader = new FXMLLoader(NavigationUtils.class.getResource("/com/example/auction/views/" + fxmlFile));
        Parent root = loader.load();

        // 2. Lấy Stage hiện tại từ sự kiện (nút bấm)
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

        // 3. Tạo Scene mới với Root vừa load và đặt vào Stage
        Scene scene = new Scene(root);

        // Nhúng file CSS chung vào màn hình mới để giữ giao diện đẹp
        scene.getStylesheets().add(NavigationUtils.class.getResource("/css/style.css").toExternalForm());

        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
    }
}
