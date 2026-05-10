package com.auction.server.model;

public class Auction {

    private int highestBid = 0;

    public synchronized void placeBid(int amount) {

        if (amount > highestBid) {

            highestBid = amount;

            System.out.println("Bid mới cao nhất: " + highestBid);

        } else {

            System.out.println("Bid bị từ chối");
        }
    }
}
