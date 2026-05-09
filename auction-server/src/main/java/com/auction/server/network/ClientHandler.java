package com.auction.server.network;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.io.PrintWriter;

public class ClientHandler extends Thread {

    private Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void broadcast(String message){
        for (ClientHandler client : Server.clients){
            try {
                PrintWriter out = new PrintWriter(client.socket.getOutputStream(), true);

                out.println(message);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void run() {
        try {
    
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            
            String message = in.readLine();

            
            System.out.println("Server nhận: " + message);

            broadcast(message);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("Server đã nhận: "+message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}