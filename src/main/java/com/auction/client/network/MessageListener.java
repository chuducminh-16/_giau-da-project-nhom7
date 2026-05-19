package com.auction.client.network;


public class MessageListener {

    private String type;    // loại lệnh: "LOGIN", "BID_UPDATE", "ERROR"...
    private String payload; // dữ liệu thực, dạng JSON string

    // Constructor đầy đủ — dùng khi tạo message để gửi đi
    public MessageListener(String type, String payload) {
        this.type    = type;
        this.payload = payload;
    }

    // Constructor rỗng — Gson bắt buộc cần để deserialize từ JSON
    public MessageListener() {}

    public String getType()    { return type; }
    public String getPayload() { return payload; }

    //  thêm toString() để dễ log và debug
    @Override
    public String toString() {
        return "Message{type='" + type + "', payload='" + payload + "'}";
    }
}