package com.auction.client;

import java.io.IOException;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneEngine {

    // Overload 1: dùng khi có ActionEvent (từ @FXML handler)
    public static void changeScene(ActionEvent event, String fxml, String title) {
        Node source = (Node) event.getSource();
        changeScene(source, fxml, title);
    }

    // Overload 2: dùng khi chỉ có Node (dùng trong Platform.runLater)
    public static void changeScene(Node node, String fxml, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                SceneEngine.class.getResource("/com/auction/client/view/fxml/" + fxml)
            );
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) node.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle(title);
            stage.setMaximized(true);
            stage.show();
        } catch (IOException e) {
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



