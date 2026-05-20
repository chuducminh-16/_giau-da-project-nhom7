package com.auction.shared.network;

/**
 * Phong bì bọc mọi dữ liệu đi qua socket.
 *
 * Hỗ trợ 2 cách tạo:
 *   new Message("LOGIN_RESPONSE", payload)          ← gọn, dùng trong handler
 *   Message.from(MessageType.LOGIN_RESPONSE, payload) ← dùng MessageType enum
 */
public class Message {

    private String  type;      // lưu dạng String để Gson serialize gọn
    private String  payload;   // nội dung JSON string
    private String  senderId;  // username người gửi, mặc định "server"
    private boolean success;   // true = OK, false = lỗi

    // Gson cần constructor rỗng để deserialize
    public Message() {}

    // ── Constructor chính — toàn bộ ClientHandler dùng cái này ──
    // type là String: "LOGIN_RESPONSE", "BID_UPDATE"...
    public Message(String type, String payload) {
        this.type     = type;
        this.payload  = payload;
        this.senderId = "server";
        this.success  = true;
    }

    // ── Constructor đầy đủ — dùng khi cần chỉ định senderId ──
    public Message(String type, String payload, String senderId) {
        this.type     = type;
        this.payload  = payload;
        this.senderId = senderId;
        this.success  = true;
    }

    // ── Constructor từ MessageType enum — giữ tương thích ──
    public Message(MessageType type, String payload, String senderId) {
        this.type     = type.name(); // chuyển enum → String khi lưu
        this.payload  = payload;
        this.senderId = senderId;
        this.success  = true;
    }

    // ── Factory methods ──────────────────────────────────────
    // Tạo message lỗi nhanh
    public static Message error(String reason) {
        Message m = new Message("ERROR", reason, "server");
        m.success = false;
        return m;
    }

    // Tạo message từ server bằng MessageType enum
    public static Message from(MessageType type, String payload) {
        return new Message(type.name(), payload, "server");
    }

    // ── Getters ──────────────────────────────────────────────
    public String  getType()     { return type;     }
    public String  getPayload()  { return payload;  }
    public String  getSenderId() { return senderId; }
    public boolean isSuccess()   { return success;  }

    // ── Setters ──────────────────────────────────────────────
    public void setType(String type)         { this.type     = type;    }
    public void setPayload(String payload)   { this.payload  = payload; }
    public void setSenderId(String id)       { this.senderId = id;      }
    public void setSuccess(boolean success)  { this.success  = success; }

    // Setter nhận enum — giữ tương thích với code cũ
    public void setType(MessageType type)    { this.type = type.name(); }

    @Override
    public String toString() {
        return "Message{type='" + type + "', senderId='" + senderId
                + "', success=" + success + ", payload='" + payload + "'}";
    }
}