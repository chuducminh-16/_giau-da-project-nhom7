package com.auction.shared.model.Entity;

public abstract class Entity {
    protected String id;    // bỏ final → Gson có thể set
    protected String name;

    public Entity(String id, String name) {
        this.id = id;
        this.name = name;
    }

    // Constructor rỗng cho Gson
    public Entity() {}

    public String getId()   { return id; }
    public String getName() { return name; }

    public void setId(String id)     { this.id = id; }
    public void setName(String name) { this.name = name; }
}
