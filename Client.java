import java.net.Socket;

public class Client {
    public static void main(String[] args) {
        try {
            System.out.println("Client đang kết nối...");

            // Kết nối tới server đang chạy
            Socket socket = new Socket("localhost", 1234);

            System.out.println("Kết nối thành công!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}