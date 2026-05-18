package com.auction.client.network;

/**
 * Phong bì bọc mọi dữ liệu đi qua socket.
 *
 * Ví dụ JSON trên đường truyền:
 * {"type":"REGISTER","payload":"{\"username\":\"abc\",\"password\":\"123\"}"}
 */
public class Message {
    private String type;     // loại lệnh: "REGISTER", "LOGIN", "BID"...
    private String payload;  // dữ liệu thực, dạng JSON string

    // Constructor
    public Message(String type, String payload) {
        this.type    = type;
        this.payload = payload;
    }

    // Gson cần constructor rỗng để deserialize
    public Message() {}

    public String getType()    { return type; }
    public String getPayload() { return payload; }
}