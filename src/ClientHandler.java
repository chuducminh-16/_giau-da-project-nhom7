import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class ClientHandler extends Thread {

    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            // Tạo luồng để đọc dữ liệu
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            
            String message = in.readLine();

            
            System.out.println("Server nhận: " + message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}