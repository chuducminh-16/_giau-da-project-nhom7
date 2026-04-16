import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    public static void main(String[] args) {
        try {
            System.out.println("Server đang chạy...");

            //tạo server socket
            ServerSocket server = new ServerSocket(1234);

            while (true) {
                Socket client = server.accept();
                System.out.println("Có client kết nối!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}