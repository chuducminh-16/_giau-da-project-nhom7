package com.auction.server.network;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import com.auction.server.model.Auction;


public class Server {

    public static ArrayList<ClientHandler> clients = new ArrayList<>();

    public static Auction auction = new Auction();
    public static void main(String[] args) {
        try {
            System.out.println("Server đang chạy...");

            ServerSocket server = new ServerSocket(1234);

            while (true) {
                Socket client = server.accept();

                ClientHandler handler = new ClientHandler(client);

                clients.add(handler);
                
                handler.start();

                System.out.println("Có client kết nối!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}