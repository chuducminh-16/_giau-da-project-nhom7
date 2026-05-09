package com.auction.client.network;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;


public class Client {
    public static void main(String[] args) {
        try {
            System.out.println("Client đang kết nối...");

            Socket socket = new Socket("localhost", 1234);

            System.out.println("Kết nối thành công!");


            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            out.println("HELLO SERVER");

            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            String response = in.readLine();
            System.out.println("Client nhận: "+response);



        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}