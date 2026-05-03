package com.auction.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class AuctionClientApp extends Application {
    @Override
    public void start(Stage stage) throws IOException {

//        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
//        Scene scene = new Scene(fxmlLoader.load(), 800, 600);

//        FXMLLoader fxmlLoader = new FXMLLoader(AuctionClientApp.class.getResource("detail-view.fxml"));
//        Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

        FXMLLoader fxmlLoader = new FXMLLoader(AuctionClientApp.class.getResource("home-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);

        // Add global CSS
        scene.getStylesheets().add(getClass().getResource("css/style.css").toExternalForm());

        stage.setTitle("UET Auction - Login");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}