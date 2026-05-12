package com.auction.client;

import com.auction.client.utils.NavigationUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import java.io.IOException;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField emailInput;
    @FXML private PasswordField passwordInput;
    @FXML private StackPane rootPane;
    @FXML private ImageView backgroundImage;
    @FXML private Button btnSignUp;

    @FXML
    public void initialize() {
        // "Trói" chiều rộng của ảnh dính chặt vào chiều rộng của khung
        backgroundImage.fitWidthProperty().bind(rootPane.widthProperty());

        // "Trói" chiều cao của ảnh dính chặt vào chiều cao của khung
        backgroundImage.fitHeightProperty().bind(rootPane.heightProperty());
    }

    @FXML
    protected void onLoginClick(ActionEvent event) {
        String email = emailInput.getText();
        String password = passwordInput.getText();

        if (email.isEmpty() || password.isEmpty()) {
            System.out.println("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        System.out.println("Đang đăng nhập với: " + email);

        // TODO: Gửi request lên Server kiểm tra DB ở đây

        // Giả lập đăng nhập thành công và chuyển sang màn hình Chi tiết đấu giá
        try {
            NavigationUtils.switchScene(event, "detail-view.fxml", "UET Auction - Đấu giá trực tiếp");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Lỗi chuyển màn hình: " + e.getMessage());
        }
    }

    @FXML
    public void onLoginClicked(ActionEvent event) {
        // Giả sử đăng nhập thành công
        SceneEngine.changeScene(event, "home-view.fxml", "The Curator - Trang chủ");
    }

    @FXML
    public void onGoToRegister(ActionEvent event) {
        SceneEngine.changeScene(event, "profile-view.fxml", "The Curator - Đăng ký tài khoản");
    }

    // Hàm xử lý khi bấm nút Sign Up
    @FXML
    protected void onSignUpClick(ActionEvent event) throws IOException {
        // Bước 1: Tải file giao diện profile-view.fxml
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("profile-view.fxml"));
        Scene profileScene = new Scene(fxmlLoader.load());

        // Bước 2: Lấy ra stage hiện tại đang chứa nút Sign Up
        Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();

        // Bước 3: Thay scene hiện tại bằng cảnh Profile và hiển thị
        currentStage.setMaximized(false);
        currentStage.setScene(profileScene);
        currentStage.setMaximized(true);

        currentStage.show();
    }
}