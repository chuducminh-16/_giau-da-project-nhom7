package com.auction.server.model;

public class Auction {

    private int highestBid = 0;

    private String highestBidder = "Chưa có ai";
   
    public synchronized boolean placeBid(String username, int amount) {

        if (amount > highestBid) {

            highestBid = amount;

            highestBidder = username;

            return true;
        }

        return false;
    }

    public int getHighestBid() {

        return highestBid;
    }

    public String getHighestBidder() {

        return highestBidder;
    }
}