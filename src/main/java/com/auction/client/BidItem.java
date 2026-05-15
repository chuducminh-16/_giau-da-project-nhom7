package com.auction.client;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class BidItem {
    private final StringProperty time;
    private final StringProperty user;
    private final StringProperty amount;

    public BidItem(String time, String user, String amount) {
        this.time = new SimpleStringProperty(time);
        this.user = new SimpleStringProperty(user);
        this.amount = new SimpleStringProperty(amount);
    }
    public StringProperty timeProperty() { return time; }
    public StringProperty userProperty() { return user; }
    public StringProperty amountProperty() { return amount; }
}
