module com.example.giaodien1 {
    requires javafx.controls;
    requires javafx.fxml;
    //requires javafx.web;

    opens com.example.giaodien1 to javafx.fxml;
    exports com.example.giaodien1;
}