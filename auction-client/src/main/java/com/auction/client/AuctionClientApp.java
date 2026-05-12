package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;

public class AuctionClientApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {

        FXMLLoader fxmlLoader = new FXMLLoader(AuctionClientApp.class.getResource("login-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        // Add global CSS
        scene.getStylesheets().add(getClass().getResource("css/style.css").toExternalForm());

        stage.setTitle("UET Auction - Login");
        stage.setScene(scene);

        stage.setMaximized(true);

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}