package com.auction.client;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class SceneEngine {
    public static void changeScene(ActionEvent event, String fxmlFile, String title) {
        try {
            Parent root = FXMLLoader.load(SceneEngine.class.getResource(fxmlFile));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();

            stage.setMaximized(false);
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.setMaximized(true);

            stage.show();
        } catch (IOException e) {
            System.err.println("Không thể chuyển màn hình: " + fxmlFile);
            e.printStackTrace();
        }
    }

    /**
     * Gọi hàm này trong main() / start() để màn hình đầu tiên cũng full.
     * Ví dụ: SceneEngine.applyFullscreen(primaryStage);
     */
    public static void applyFullscreen(Stage stage) {
        stage.setMaximized(true);
    }
}
