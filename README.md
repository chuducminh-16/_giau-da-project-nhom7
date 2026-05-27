GIAU_DA — Online Auction System

Lập trình nâng cao — 252_UET.CS2043 16
Nhóm: 7 | Học kỳ: Học kỳ 2, năm học 2025 - 2026.


Authors:
1. Đồng Hải Dương - 2502
2. Trịnh Văn Hiệp - 2502
3. Chử Đức Minh - 25023317
4. Nguyễn Khắc Nhật Quang - 2502


Description: 

GIAU_DA là hệ thống đấu giá trực tuyến (Online Auction System) được phát triển bằng Java, áp dụng kiến trúc Client–Server thực tế với giao tiếp qua TCP Socket và cơ sở dữ liệu MySQL. Hệ thống cho phép nhiều người dùng đồng thời tham gia cạnh tranh giá để mua sản phẩm trong một khoảng thời gian xác định, tham khảo mô hình eBay Auctions.

Dự án thể hiện toàn diện các nguyên lý Lập trình Hướng Đối Tượng (OOP), các Design Pattern phổ biến, kỹ thuật Concurrent Programming, và Realtime Communication trong một ứng dụng thực tế.


Key Features:

🔐 Quản lý người dùng đa vai trò — Bidder, Seller, Admin với quyền hạn riêng biệt

📦 Quản lý sản phẩm đa loại — Art, Electronics, Vehicle với thông tin đặc trưng

⚡ Đấu giá realtime — Cập nhật giá tức thì cho tất cả client đang xem phiên, không dùng polling

🔒 Xử lý đồng thời an toàn — ReentrantLock per-product ngăn chặn race condition, lost update

🤖 Auto-Bidding — Tự động đặt giá thay người dùng với ngân sách và bước giá tùy chỉnh

🛡️ Anti-Sniping — Tự động gia hạn phiên khi có bid trong 60 giây cuối

📊 Biểu đồ giá realtime — Line chart cập nhật động theo từng lượt bid

👑 Admin Dashboard — Quản lý toàn bộ sản phẩm, tài khoản, phiên đấu giá

🖼️ Upload ảnh sản phẩm — Tích hợp ImgBB API lưu trữ ảnh đám mây

🔔 Toast Notification — Thông báo popup khi có bid mới

⏳ Đồng hồ đếm ngược realtime — Hiển thị thời gian còn lại cho từng phiên

📜 Lịch sử đấu giá cá nhân — Theo dõi các phiên đã tham gia, thắng/thua

🔄 CI/CD tự động — GitHub Actions build & test khi push lên main


Demo:
Screenshots

Màn hình Đăng nhập

Trang chủ — Danh sách phiên đấu giá

Chi tiết sản phẩm

Phòng đấu giá realtime (Live Bidding)

Seller Dashboard — Quản lý sản phẩm

Admin Dashboard

Lịch sử đấu giá cá nhân


OOP Design:

Class Hierarchy
Entity (Abstract)
├── User (Abstract)
│   ├── Bidder       — Người tham gia đấu giá, có balance
│   ├── Seller       — Người đăng sản phẩm, có rating
│   └── Admin        — Quản trị viên, có adminLevel
└── Item (Abstract)
    ├── Art          — Tác phẩm nghệ thuật (artist)
    ├── Electronics  — Thiết bị điện tử (warrantyPeriod)
    └── Vehicle      — Phương tiện (mileage)

Auction             — Quản lý trung tâm một phiên đấu giá
Bid                 — Một lượt đặt giá (id, itemId, bidderId, amount, timestamp)
AuctionStatus       — Enum: OPEN → RUNNING → FINISHED → PAID / CANCELED

OOP Principles Applied
Nguyên lý Encapsulation. 
+ Nơi áp dụng: tất cả fields private/protected, truy cập qua getter/setter. Bidder.setBalance() kiểm tra giá trị âm trước khi gán.

Nguyên lý Inheritance
+ Nơi áp dụng: Bidder, Seller, Admin kế thừa User; Art, Electronics, Vehicle kế thừa Item.

Nguyên lý Polymorphism
+ Nơi áp dụng: User.getRole(), Item.getType(), Item.showDetails() được override ở từng subclass. ItemFactory trả về Item nhưng tạo đúng subclass tương ứng.

Nguyên lý Abstraction 
+ Nơi áp dụng: User và Item là abstract class. AuctionObserver là interface. AuctionException là abstract base exception.


Design Patterns:

1. Singleton Pattern
Đảm bảo duy nhất một instance cho các thành phần quan trọng:

- AuctionManager: Quản lý toàn bộ danh sách phiên đấu giá đang hoạt động. Dùng Initialization-on-demand Holder — lazy, thread-safe, không cần synchronized.

- NetworkClient: Kết nối TCP Socket duy nhất từ client đến server, dùng chung cho tất cả Controller.

- UserSession: Lưu thông tin người dùng đang đăng nhập trên client.

- BidAuto: Event Bus xử lý Auto-bid. Dùng Holder pattern, chạy trên single Worker Thread.

2. Factory Method Pattern
ItemFactory.createItem(type, ...) tạo đúng subclass Item dựa trên chuỗi type:

// Tự động tạo Art, Electronics, hoặc Vehicle tùy thuộc vào type
Item item = ItemFactory.createItem("ELECTRONICS", id, name, price, endTime, sellerId, "24");

- Input: "ART" / "ELECTRONICS" / "VEHICLE" (case-insensitive)
- Output: instance đúng subclass tương ứng
- Throws IllegalArgumentException nếu type không hợp lệ

3. Observer Pattern
Dùng để cập nhật realtime giá đấu cho tất cả client đang xem một phiên:

AuctionSubject (Abstract)           AuctionObserver (Interface)
├── subscribe(observer)             ├── onBidPlaced(BidEvent)
├── unsubscribe(observer)           └── onAuctionClosed(auctionId, winnerId, price)
├── notifyBidPlaced(event)    ←→    ClientHandler (implements AuctionObserver)
└── notifyAuctionClosed(...)

BidEvent (Immutable)
└── auctionId, productId, bid, newPrice, bidderName, timestamp

- CopyOnWriteArrayList đảm bảo thread-safe cho danh sách observer
- Mỗi notifyBidPlaced() chạy trong try-catch riêng — 1 observer lỗi không ảnh hưởng observer khác
- BidEvent là immutable — an toàn broadcast cho nhiều thread

4. Strategy / Template Method Pattern (Power-ups / DAO)
Tuy không đặt tên formal, hệ thống DAO áp dụng tư tưởng Strategy:

- UserFindDAO, UserSaveDAO, UserListDAO — phân tách trách nhiệm đọc/ghi/liệt kê
- ItemFindDAO, ItemSaveDAO, ItemListDAO — tương tự cho Item
- Dễ dàng thay thế implementation mà không ảnh hưởng tầng Service


System Architecture:
Client–Server Architecture

┌─────────────────────────────────┐     TCP Socket     ┌──────────────────────────────────┐
│           CLIENT                │◄──────────────────►│           SERVER                 │
│                                 │   JSON Messages     │                                  │
│  JavaFX (FXML + Controller)     │                     │  NetworkServer (port 9090)       │
│  ├── LoginController            │                     │  ├── ClientHandler (per-client)  │
│  ├── HomeController             │                     │  ├── UserController              │
│  ├── LiveBiddingController      │                     │  ├── AuctionRoomEngineController │
│  ├── ManageProductController    │                     │  ├── ProductController           │
│  ├── AdminController            │                     │  ├── AutoBidController           │
│  └── BidHistoryController       │                     │  └── AdminController             │
│                                 │                     │                                  │
│  NetworkClient (Singleton)      │                     │  AuctionScheduler (daemon)       │
│  UserSession (Singleton)        │                     │  BidAuto Event Bus               │
└─────────────────────────────────┘                     └────────────────┬─────────────────┘
                                                                          │
                                                                          ▼
                                                         ┌──────────────────────────────────┐
                                                         │         DATABASE                 │
                                                         │   MySQL (Railway Cloud)          │
                                                         │   ├── users                      │
                                                         │   ├── items                      │
                                                         │   ├── auctions                   │
                                                         │   ├── bids                       │
                                                         │   ├── transactions               │
                                                         │   └── auto_bids                  │
                                                         └──────────────────────────────────┘


Message Protocol:
Giao tiếp Client–Server qua JSON dòng đơn trên TCP Socket:

// Request (Client → Server)
{"type": "PLACE_BID", "payload": "{\"productId\":\"I01\",\"amount\":2500.0}"}

// Response (Server → Client)
{"type": "BID_RESULT", "payload": "{\"success\":true,\"newBid\":2500.0}"}

Các message type chính:
- Type: LOGIN / LOGIN_RESPONSE. Chiều: ↔. Mô tả: Đăng nhập

- Type: REGISTER / REGISTER_RESPONSE. Chiều: ↔. Mô tả: Đăng ký

- Type: GET_AUCTIONS / AUCTIONS_LIST. Chiều: ↔. Mô tả: Lấy danh sách phiên
  
- Type: PLACE_BID / BID_RESULT. Chiều: ↔. Mô tả: Đặt giá

- Type: BID_UPDATE. Chiều: → Client. Mô tả: Broadcast giá mới realtime

- Type: AUCTION_ENDED. Chiều: → Client. Mô tả: Thông báo phiên kết thúc

- Type: TIME_EXTENDED. Chiều: → Client. Mô tả: Thông báo gia hạn phiên

- Type: WATCH_AUCTION. Chiều: → Server. Mô tả: Thông báo gia hạn phiên

- Type: REGISTER_AUTO_BID. Chiều: ↔. Mô tả: Thông báo gia hạn phiên


MVC Structure:
Client (JavaFX):

View    — FXML files (login-view, home-view, live-bidding-view, ...)
           + CSS styles
Controller — *Controller.java (LoginController, HomeController, ...)
           + MessageHandler (tách xử lý mạng ra khỏi Controller)
Model   — shared/model/Entity (User, Item, Auction, Bid)
           + session (UserSession, SelectedProductSession)
           
Server:

Controller — UserController, AuctionRoomEngineController, ProductController, ...
Service    — UserService, BidPlacementService, AuctionProductService, ...
DAO        — UserFindDAO, BidDAO, AuctionDAO, ... (chỉ server truy cập DB)


Key Technical Implementations:
- Concurrent Bidding — Thread-Safe với ReentrantLock

// BidPlacementService.java
private static final ConcurrentHashMap<String, ReentrantLock> lockMap = new ConcurrentHashMap<>();

public BidOutcome placeBid(String productId, String bidderId, double amount) {
    ReentrantLock lock = getLock(productId);  // Lock per sản phẩm, không lock toàn hệ thống
    lock.lock();
    try {
        // SELECT FOR UPDATE → kiểm tra giá → INSERT bid → UPDATE price → COMMIT
        return placeBidInTransaction(productId, bidderId, amount);
    } finally {
        lock.unlock();
    }
}

Đảm bảo:
✅ Không lost update khi nhiều bidder đặt giá cùng lúc
✅ Không hai người cùng thắng
✅ Giá không bị rollback ngoài ý muốn
✅ Seller không tự bid vào phiên của mình

- Anti-Sniping Algorithm
Nếu có bid trong 60 giây cuối của phiên → tự động gia hạn thêm 60 giây:

// Trong placeBidInTransaction()
long secondsLeft = Duration.between(LocalDateTime.now(), endTime).getSeconds();
if (secondsLeft <= SNIPE_WINDOW_SECONDS) {          // 60 giây
    extendEndTime(conn, auctionId, SNIPE_EXTEND_SECONDS); // +60 giây
    newEndTimeStr = getEndTime(conn, auctionId);
}
Broadcast TIME_EXTENDED tới tất cả client → đồng hồ đếm ngược cập nhật lại tức thì.

- Auto-Bidding System

Bidder đặt Auto-bid (maxBid, increment)
         │
         ▼
   Lưu vào DB (auto_bids table)
         │
         ▼
Khi có bid mới → AutoBidService.triggerAutoBid()
         │
         ├── Lấy tất cả bidder có auto-bid active, maxBid > currentPrice, loại trừ người vừa bid
         ├── Ưu tiên: maxBid cao nhất → registered_at cũ nhất (PriorityQueue)
         ├── Tính toán: nextBid = currentPrice + increment
         └── Nếu nextBid ≤ maxBid → placeBid() → Broadcast BID_UPDATE
         
BidAuto Event Bus chạy trên single worker thread (Producer-Consumer pattern) để tránh race condition giữa các bot.

- Realtime Update — Observer via Socket

Bidder đặt giá thành công
         │
         ▼
Server: broadcastToAuction(itemId, BID_UPDATE)
         │
         ├── ClientHandler.isWatchingAuction(itemId) → gửi message
         ├── ClientHandler.isWatchingAuction(itemId) → gửi message
         └── ... (tất cả client đang xem phiên)
         │
         ▼
Client: NetworkClient listener nhận message
         │
         ▼
LiveBiddingController: cập nhật giá, bảng lịch sử, biểu đồ

- Exception Hierarchy

AuctionException (Abstract — RuntimeException)
├── InvalidBidException          — Giá đặt thấp hơn/bằng giá hiện tại
├── AuctionClosedException       — Phiên FINISHED / CANCELED / PAID
├── ProductNotFoundException     — Sản phẩm không tồn tại
├── UserNotFoundException        — Email sai / user không tồn tại
├── DuplicateAccountException    — Email / username đã được dùng
├── UnauthorizedActionException  — Không có quyền thực hiện hành động
├── InvalidProductDataException  — Tên rỗng, giá âm, endTime đã qua
├── DatabaseException            — Wrap SQLException
└── NetworkException             — Lỗi kết nối socket / server


User Roles & Features:
Bidder

- Đăng ký / đăng nhập
- Xem danh sách phiên đấu giá đang diễn ra
- Xem chi tiết sản phẩm với đồng hồ đếm ngược realtime
- Tham gia đặt giá trong phòng live bidding
- Kích hoạt Auto-bid với maxBid và bước giá tùy chỉnh
- Xem biểu đồ diễn biến giá realtime (line chart)
- Xem lịch sử đấu giá cá nhân (thắng / thua / đang diễn ra)
- Nhận thông báo toast khi có bid mới

Seller

- Tất cả quyền của Bidder
- Thêm / sửa / xóa sản phẩm (Art, Electronics, Vehicle)
- Upload ảnh sản phẩm lên cloud (ImgBB)
- Theo dõi danh sách sản phẩm đã đăng và trạng thái

Admin

- Xem và quản lý toàn bộ sản phẩm
- Xem và xóa tài khoản người dùng (trừ tài khoản đang đăng nhập)
- Xem tất cả phiên đấu giá với bộ lọc theo trạng thái
- Đóng phiên đấu giá bất kỳ
- Dashboard với thống kê tổng quan
- Tự động chuyển hướng vào Admin Panel sau khi đăng nhập


Auction Lifecycle:
OPEN → RUNNING → FINISHED → PAID
                           → CANCELED (nếu không có bid nào)

- Trạng thái: OPEN - Vừa tạo, chưa đến giờ bắt đầu

- Trạng thái: RUNNING - Đang diễn ra, nhận bid

- Trạng thái: FINISHED - Hết giờ, có người thắng, chờ thanh toán

- Trạng thái: PAID - Người thắng đã thanh toán

- Trạng thái: CANCELED - Bị hủy hoặc kết thúc mà không có bid

AuctionScheduler chạy mỗi 10 giây để tự động đóng các phiên hết giờ, xác định winner, lưu transaction, và broadcast AUCTION_ENDED.


Database Schema:

users        (id, username, email, password, balance, role, rating, admin_level)
items        (id, name, starting_price, current_price, end_time, type, seller_id,
              status, description, bid_increment, image_path)
auctions     (id, item_id, seller_id, current_price, status, end_time, created_at)
bids         (id, item_id, bidder_id, bid_price, bid_time)
transactions (id, item_id, winner_id, final_price, transaction_time)
auto_bids    (item_id, bidder_id, max_bid, increment, active, registered_at)


Unit Tests:
Tests được viết bằng JUnit 5, bao phủ logic quan trọng không cần kết nối DB:

- Test File: ObserverPatternTest. Phạm vi kiểm tra: Subscribe/Unsubscribe, notify bid placed, notify auction closed, isolation khi observer throw exception, BidEvent getters

- Test File: AuctionTest. Phạm vi kiểm tra: Auction status (OPEN/RUNNING/FINISHED), isActive(), refreshStatus(), setStatus(String), AuctionManager Singleton, add/remove/findById, getActiveAuctions

- Test File: UserTest. Phạm vi kiểm tra: getRole() cho Bidder/Seller/Admin, checkPassword(), setBalance() với giá trị âm, Polymorphism qua User reference

- Test File: ItemTest. Phạm vi kiểm tra: getType() và showDetails() cho Art / Electronics / Vehicle, ItemFactory tạo đúng subclass, ItemFactory với type null/invalid/lowercase

- Test File: UserServiceTest. Phạm vi kiểm tra: Validate input đăng ký (null, rỗng, email sai, password ngắn), validate input đăng nhập (null, rỗng, blank)

- Test File: AuctionServiceTest. Phạm vi kiểm tra: BidResult enum, BidOutcome getters, validateBid logic (giá hợp lệ/thấp/phiên đóng/hết giờ/null), Anti-sniping logic, Concurrent bidding với 50 threads

- Test File: SnipeGuardServiceTest. Phạm vi kiểm tra: Bid trong/ngoài cửa sổ 60s, ranh giới chính xác 60/61s, cascade 3 lần gia hạn, SnipeGuardResult getters

Chạy toàn bộ test:
mvn test


CI/CD:
GitHub Actions tự động chạy khi push lên main hoặc tạo Pull Request:

Job 1 — Build & Test:
- Checkout code
- Set up JDK 21 (Temurin)
- Cache Maven packages
- mvn test --batch-mode
- Upload test report (Surefire)

Job 2 — Package JAR (chỉ khi push, sau khi CI pass):
- mvn package -DskipTests
- Upload JAR artifact (lưu 30 ngày)


Project Structur:

src/
├── main/java/com/auction/
│   ├── client/
│   │   ├── controller/          # JavaFX Controllers (UI logic)
│   │   ├── handler/             # Message handlers (tách khỏi Controller)
│   │   │   ├── admin/
│   │   │   ├── bidhistory/
│   │   │   ├── detail/
│   │   │   ├── home/
│   │   │   ├── livebidding/
│   │   │   ├── product/
│   │   │   └── profile/
│   │   ├── network/             # NetworkClient, Message
│   │   ├── session/             # UserSession, SelectedProductSession, AutoBidSession
│   │   └── utils/               # ImageUploadHandler, ToastNotification, ...
│   ├── server/
│   │   ├── controller/          # Server-side Controllers (route & dispatch)
│   │   ├── dao/                 # Data Access Objects
│   │   │   ├── auction/
│   │   │   ├── bid/
│   │   │   ├── item/
│   │   │   ├── transaction/
│   │   │   └── user/
│   │   ├── network/             # NetworkServer, ClientHandler, ImageHandler
│   │   ├── scheduler/           # AuctionScheduler (auto-close expired auctions)
│   │   ├── service/             # Business logic
│   │   │   └── auction/         # BidPlacementService, AuctionQueryService, ...
│   │   └── autobid/             # BidAuto Event Bus, RecordBid
│   └── shared/
│       ├── exception/           # AuctionException hierarchy
│       ├── model/Entity/        # User, Item, Auction, Bid (shared model)
│       └── pattern/             # AuctionManager, ItemFactory, Observer classes
├── main/resources/
│   └── com/auction/client/view/
│       ├── fxml/                # FXML layout files
│       └── css/                 # style.css
└── test/java/com/auction/       # JUnit 5 test classes


Installation & Setup:
Prerequisites

- Java JDK - Version 21+
- Maven - Version 3.9+
- MySQL - Version 8.0+ (hoặc dùng DB đã cấu hình sẵn)


Database Setup:

-- Chạy file database.sql để khởi tạo schema và dữ liệu mẫu
SOURCE database.sql;

Cấu hình kết nối DB trong DatabaseConnection.java:
private static final String URL  = "jdbc:mysql://YOUR_HOST:PORT/YOUR_DB";
private static final String USER = "YOUR_USER";
private static final String PASSWORD = "YOUR_PASSWORD";

Build & Run

# Clone repository
git clone [LINK_GITHUB_REPO]

# Build
mvn clean compile

# Chạy Server (Terminal 1)
mvn exec:java -Dexec.mainClass="com.auction.server.Main"

# Chạy Client (Terminal 2)
mvn exec:java -Dexec.mainClass="com.auction.client.Launcher"


Default Test Accounts:
- Username: admin. Password: admin. Role: Admin
- Username: seller1. Password: sell123. Role: Seller
- Username: minh_dz. Password: 123456. Role: Bidder
- Username: test_user. Password: 1111. Role: Bidder


Dependencies:
Được quản lý bởi Maven (pom.xml):

+ Dependency: javafx-controls. Version: 21.0.2. Mục đích: UI framework

+ Dependency: javafx-fxml. Version: 21.0.2. Mục đích: FXML loader

+ Dependency: javafx-swing. Version: 21.0.2. Mục đích: SwingFXUtils cho ảnh

+ Dependency: gson. Version: 2.14.0. Mục đích: JSON serialize/deserialize qua socket

+ Dependency: mysql-connector-j. Version: 8.3.0. Mục đích: Kết nối MySQL

+ Dependency: junit-jupiter-api. Version: 5.10.0. Mục đích: Unit testing

+ Dependency: junit-jupiter-engine. Version: 5.10.0. Mục đích: JUnit 5 runner


Known Limitations:

- Không hỗ trợ HTTPS / TLS — Socket giao tiếp plain text (phù hợp cho môi trường học thuật)
- Password lưu plain text — Chưa hash password (cần bcrypt trong production)
- Một server duy nhất — Chưa hỗ trợ load balancing hay clustering
- File I/O ảnh — Upload ảnh qua ImgBB yêu cầu kết nối internet
- JavaFX HiDPI — Một số scale màn hình có thể cần điều chỉnh


Repository: 
🔗 GitHub: https://github.com/chuducminh-16/_giau-da-project-nhom7 




