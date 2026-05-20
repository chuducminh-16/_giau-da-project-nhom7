# API Contract — Auction System

## 1. Cổng kết nối
- Server port: 9090
- Host: localhost (test) / IP thật khi demo

## 2. Format message (BẤT BIẾN — không ai được đổi)
{"type":"LOAI_LENH","payload":"{...json...}"}

## 3. Package structure (BẤT BIẾN)
com.auction.shared.network.Message       → Networking viết, tất cả dùng
com.auction.shared.network.MessageType   → Networking viết, tất cả dùng
com.auction.client.session.UserSession   → Networking viết, Frontend dùng
com.auction.model.Entity.User.User       → Backend1 viết, tất cả dùng
com.auction.model.Entity.Auction_Bid.Auction → Backend1 viết, tất cả dùng

## 4. UserService interface (Backend1 cam kết)
- User login(String email, String password)
- RegisterResult register(...)
- enum RegisterResult { SUCCESS, EMAIL_EXISTS, USERNAME_EXISTS }

## 5. AuctionService interface (Backend2 cam kết)
- List<Auction> getActiveAuctions()
- BidOutcome placeBid(String productId, String userId, double amount)
- AuctionCloseResult closeExpiredAuctions()
- ExtendResult checkAndExtend(String auctionId)
- enum BidResult { SUCCESS, PRICE_TOO_LOW, AUCTION_ENDED, AUCTION_NOT_FOUND }

## 6. User model (Backend1 cam kết — KHÔNG được đổi tên getter)
- String getId()
- String getUsername()
- String getEmail()
- String getRole()  ← trả về String, KHÔNG phải Enum