package com.auction.server;

import com.auction.server.model.Auction;

public class TestAuction {

    public static void main(String[] args) {

        Auction auction = new Auction();

        Thread user1 = new Thread(() -> {
            auction.placeBid(1000);
        });

        Thread user2 = new Thread(() -> {
            auction.placeBid(1200);
        });

        Thread user3 = new Thread(() -> {
            auction.placeBid(900);
        });

        user1.start();
        user2.start();
        user3.start();
    }
}
