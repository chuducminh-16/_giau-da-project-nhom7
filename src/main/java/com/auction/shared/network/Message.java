package com.auction.shared.network;

public class Message {

    private MessageType type;    // loại tin nhắn
    private String payload;       // nội dung dạng JSON string (linh hoạt)
    private String senderId;      // username của người gửi
    private boolean success;      // true = OK, false = lỗi

    // Gson cần constructor rỗng để deserialize
    public Message() {}

    // Constructor tiện dụng nhất
    public Message(MessageType type, String payload, String senderId) {
        this.type = type;
        this.payload = payload;
        this.senderId = senderId;
        this.success = true;
    }

    // Factory method: tạo message lỗi nhanh
    public static Message error(String reason) {
        Message m = new Message(MessageType.ERROR, reason, "server");
        m.success = false;
        return m;
    }

    // Factory method: tạo message từ server nhanh
    public static Message from(MessageType type, String payload) {
        return new Message(type, payload, "server");
    }

    // ── Getters & Setters ──────────────────────────────
    public MessageType getType()    { return type; }
    public String getPayload()       { return payload; }
    public String getSenderId()      { return senderId; }
    public boolean isSuccess()       { return success; }

    public void setType(MessageType type)    { this.type = type; }
    public void setPayload(String payload)   { this.payload = payload; }
    public void setSenderId(String id)       { this.senderId = id; }
    public void setSuccess(boolean success)  { this.success = success; }

    public String toString() {
        return "[" + type + "] from=" + senderId + " payload=" + payload;
    }
}
