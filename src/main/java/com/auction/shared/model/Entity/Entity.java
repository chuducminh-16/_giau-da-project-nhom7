package com.auction.shared.model.Entity;

import com.google.gson.annotations.SerializedName;

public abstract class Entity {

    // Dùng @SerializedName để Gson không cần dùng reflection trực tiếp vào field
    @SerializedName("id")
    protected String id;

    @SerializedName("name")
    protected String name;

    public Entity(String id, String name) {
        this.id   = id;
        this.name = name;
    }

    // Constructor rỗng cho Gson — BẮT BUỘC
    public Entity() {}

    public String getId()   { return id; }
    public String getName() { return name; }

    public void setId(String id)     { this.id = id; }
    public void setName(String name) { this.name = name; }
}