package com.auction.server.network;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;
import java.io.PrintWriter;
import com.auction.server.model.Auction;
import com.google.gson.Gson;
import com.auction.server.model.Message;

public class ClientHandler extends Thread {

    private Socket socket;
    private String username;
    private boolean joined = false;


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

            
           PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

           String message;

           while  ((message = in.readLine()) != null) {
            

            System.out.println("Server nhận: " + message);

            Gson gson = new Gson();

            Message msg = gson.fromJson(message, Message.class);

            username = msg.username;

            int amount = msg.amount;

            if (!joined) {

                broadcast(username + "đã tham gia phòng đấu giá");

                joined = true;
            }
    

            boolean success = Server.auction.placeBid(username, amount);

            if (success) {
                System.out.println("Bid mới cao nhất: " + amount);

                broadcast(
                    username + " bid: " + amount
                    + "\nGiá cao nhất hiện tại: "
                    + Server.auction.getHighestBid()
                    + "\nNgười dẫn đầu: "
                    + Server.auction.getHighestBidder()
                );
                
            } else {
                out.println("Bid thất bại! Giá hiện tại là " + Server.auction.getHighestBid());
            }
           }

        } catch (Exception e) {

            System.out.println(username + " đã rời phòng");

            Server.clients.remove(this);

            broadcast(username + " đã rời phòng");
            

        }
    }
}