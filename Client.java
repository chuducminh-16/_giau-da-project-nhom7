import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    public static void main(String[] args) {
        try {
            System.out.println("Client đang kết nối...");

            Socket socket = new Socket("localhost", 1234);

            System.out.println("Kết nối thành công!");

            // Tạo luồng gửi dữ liệu lên server
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("HELLO SERVER");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}