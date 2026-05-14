package com.auction.client.network;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Scanner;


public class Client {
    public static void main(String[] args) {
        try {
            System.out.println("Client đang kết nối...");

            Socket socket = new Socket("localhost", 1234);

            System.out.println("Kết nối thành công!");


            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


            Thread receiveThread = new Thread(() -> {
                try {
                    while (true) {
                        String response = in.readLine();

                        if (response != null){
                            System.out.println(response);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            receiveThread.start();

            Scanner scanner = new Scanner(System.in);

            System.out.print("Nhập tên: ");

            String username = scanner.nextLine();

            while (true) {
                System.out.print("Nhập giá bid: ");

                int bid = scanner.nextInt();

                String json =
                    "{\"type\":\"BID\","
                    + "\"username\":\""
                    + username
                    + "\","
                    + "\"amount\":"
                    + bid
                    + "}";
                out.println(json);
            }





        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}