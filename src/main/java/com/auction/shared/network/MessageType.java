package com.auction.shared.network;

public enum MessageType {

    // ── Client → Server ──────────────────────────────
    LOGIN,           // client gửi username + password
    REGISTER,        // client gửi thông tin đăng ký
    LOGOUT,          // client thoát

    GET_AUCTIONS,    // lấy danh sách phiên đấu giá
    GET_AUCTION_DETAIL, // lấy chi tiết 1 phiên

    PLACE_BID,       // client đặt giá

    CREATE_AUCTION,  // seller tạo phiên mới
    UPDATE_AUCTION,  // seller sửa phiên
    DELETE_AUCTION,  // seller xoá phiên

    // ── Server → Client ──────────────────────────────
    LOGIN_SUCCESS,   // đăng nhập thành công, trả về token
    LOGIN_FAIL,      // sai mật khẩu / không tồn tại
    REGISTER_SUCCESS,
    REGISTER_FAIL,

    AUCTION_LIST,    // server trả danh sách phiên
    AUCTION_DETAIL,  // server trả chi tiết phiên

    BID_SUCCESS,     // đặt giá thành công
    BID_FAIL,        // đặt giá thất bại (lỗi giá, phiên đóng...)

    // ── Server broadcast → TẤT CẢ client ────────────
    BID_UPDATE,      // có bid mới → notify realtime cho mọi người
    AUCTION_ENDED,   // phiên kết thúc → thông báo người thắng
    AUCTION_EXTENDED, // anti-sniping: phiên được gia hạn

    // ── Chung ────────────────────────────────────────
    ERROR,           // lỗi chung
    PING,            // kiểm tra kết nối còn sống không
    PONG             // phản hồi ping
}