# GIAU_DA — Online Auction System

- Lập trình nâng cao — 252_UET.CS2043 16
- Nhóm: 7 | Học kỳ 2, năm học 2025 - 2026.


# Authors:
1. Đồng Hải Dương - MSV: 25023206 - Networking
2. Trịnh Văn Hiệp - MSV: 25023242 - BackEnd
3. Chử Đức Minh - MSV: 25023317 - FrontEnd
4. Nguyễn Khắc Nhật Quang - MSV: 25023364 - Database


# 1.  Mô tả bài toán và phạm vi hệ thống 

- GIAU_DA là hệ thống đấu giá trực tuyến được phát triển bằng Java, áp dụng kiến trúc Client–Server thực tế. Hệ thống cho phép nhiều người dùng đồng thời tham gia cạnh tranh giá để mua sản phẩm trong một khoảng thời gian xác định, với kết quả được cập nhật tức thì tới người xem. Tham khảo mô hình eBay Auctions.

- Phạm vi hệ thống bao gồm:

Ba vai trò người dùng: Bidder (người đấu giá), Seller (người bán), Admin (quản trị viên)

Ba loại sản phẩm: Art (tác phẩm nghệ thuật), Electronics (điện tử), Vehicle (phương tiện)

Giao tiếp realtime qua TCP Socket với định dạng JSON

Lưu trữ dữ liệu trên MySQL (cloud hoặc local)

Giao diện desktop bằng JavaFX / FXML

- Dự án thể hiện toàn diện các nguyên lý Lập trình Hướng Đối Tượng (OOP), các Design Pattern phổ biến, kỹ thuật Concurrent Programming, và Realtime Communication trong một ứng dụng thực tế (chi tiết ở cuối).

# 2. Công nghệ sử dụng và môi trường chạy

Ngôn ngữ & Framework

+ Ngôn ngữ chính: ```Java```. Version: ```21+```

+ Build tool: ```Apache Maven```. Version: ```3.9+```

+ UI framework: ```JavaFX```. Version: ```21.0.2```

+ JSON serialization: ```Gson```. Version: ```2.14.0```

+ Database driver: ```MySQL Connector/J```. Version: ```8.3.0```

+ Unit testing: ```JUnit Jupiter```. Version: ```5.10.0```


Môi trường chạy

- JDK 21+ — bắt buộc (dự án dùng Java records, switch expression, sealed classes)
- Maven 3.9+ — quản lý dependency và build
- MySQL 8.0+ — cơ sở dữ liệu (hoặc dùng kết nối cloud đã cấu hình sẵn trong source)
- Kết nối Internet — cần thiết cho tính năng upload ảnh qua ImgBB API


Hệ điều hành hỗ trợ

- Windows 10/11:  Hỗ trợ đầy đủ
- macOS (Intel & Apple Silicon):  Hỗ trợ đầy đủ
- Linux (Ubuntu 20.04+, Debian):  Hỗ trợ đầy đủ

```
Lưu ý trên Linux/macOS: JavaFX yêu cầu môi trường đồ họa (X11 hoặc Wayland trên Linux). Nếu chạy trên server headless, chỉ có thể chạy Server — không chạy được Client GUI.
```

# 3. Cấu trúc thư mục
```
giau-da-project-nhom7/
|
+-- src/
|   +-- main/
|   |   +-- java/com/auction/
|   |   |   +-- client/                        # Toàn bộ code phía Client
|   |   |   |   +-- controller/                # JavaFX Controllers (UI logic)
|   |   |   |   |   +-- LoginController.java
|   |   |   |   |   +-- HomeController.java
|   |   |   |   |   +-- LiveBiddingController.java
|   |   |   |   |   +-- ManageProductController.java
|   |   |   |   |   +-- AdminController.java
|   |   |   |   |   +-- BidHistoryController.java
|   |   |   |   |   +-- ProfileController.java
|   |   |   |   |   +-- RegisterController.java
|   |   |   |   |   +-- SnipeGuardLiveBiddingController.java
|   |   |   |   |   +-- WalletController.java
|   |   |   |   |   \-- DetailController.java
|   |   |   |   +-- handler/                   # Tách xử lý message ra khỏi Controller
|   |   |   |   |   +-- home/
|   |   |   |   |       +-- HomeMessageHandler.java
|   |   |   |   |       +-- HomeSearchFilter.java
|   |   |   |   |       +-- HomeTableSetup.java
|   |   |   |   |   +-- livebidding/
|   |   |   |   |       +-- AutoBidHandler.java
|   |   |   |   |       +-- CountdownHandler.java
|   |   |   |   |       +-- LiveBiddingChartManager.java
|   |   |   |   |   +-- admin/
|   |   |   |   |       +-- AdminMessageHandler.java
|   |   |   |   |       +-- AdminTableSetup.java
|   |   |   |   |   +-- bidhistory/
|   |   |   |   |       +-- BidHistoryMessageHandler.java
|   |   |   |   |       +-- BidHistoryTableSetup.java
|   |   |   |   |   +-- detail/
|   |   |   |   |       +-- DetailMessageHandler.java
|   |   |   |   |   +-- product/
|   |   |   |   |       +-- ProductFormValidator.java
|   |   |   |   |       +-- ProductMessageHandler.java
|   |   |   |   |       +-- ProductTableSetup.java
|   |   |   |   |   +-- bidding/
|   |   |   |   |       +-- BiddingMessageHandler.java
|   |   |   |   |   \-- profile/
|   |   |   |   |       +-- ProfileMessageHandler.java
|   |   |   |   +-- model/
|   |   |   |   |   +-- BidItem.java
|   |   |   |   +-- network/                   # TCP Socket client
|   |   |   |   |   +-- NetworkClient.java     # Singleton TCP connection
|   |   |   |   |   \-- Message.java           # JSON message envelope
|   |   |   |   +-- session/                   # Quản lý trạng thái phiên
|   |   |   |   |   +-- UserSession.java
|   |   |   |   |   +-- SelectedProductSession.java
|   |   |   |   |   \-- AutoBidSession.java
|   |   |   |   \-- utils/                     # Tiện ích
|   |   |   |   |   +-- ImageUploadHandler.java
|   |   |   |       \-- ToastNotification.java
|   |   |   |   +-- AuctionClientApp.java
|   |   |   |   +-- BidItem.java
|   |   |   |   +-- Launcher.java
|   |   |   |   +-- SceneEngine.java

|   |   |   +-- server/                        # Toàn bộ code phía Server
|   |   |   |   +-- Main.java                  # Entry point khởi động Server
|   |   |   |   +-- controller/                # Điều phối request → Service
|   |   |   |   |   +-- UserController.java
|   |   |   |   |   +-- AuctionRoomEngineController.java
|   |   |   |   |   +-- ProductController.java
|   |   |   |   |   +-- AutoBidController.java
|   |   |   |   |   +-- SnipeGuardController.java
|   |   |   |   |   +-- WalletController.java
|   |   |   |   |   \-- AdminController.java
|   |   |   |   +-- dao/                       # Data Access Objects (chỉ server dùng)
|   |   |   |   |   +-- auction/AuctionDAO.java
|   |   |   |   |   +-- bid/
|   |   |   |   |       +-- BidDAO.java
|   |   |   |   |       +-- BidHistoryDAO.java
|   |   |   |   |   +-- item/
|   |   |   |   |       +-- ItemFindDAO.java
|   |   |   |   |       +-- ItemSaveDAO.java
|   |   |   |   |       +-- ItemListDAO.java
|   |   |   |   |   +-- transaction/TransactionDAO.java
|   |   |   |   |   \-- user/
|   |   |   |   |       +-- UserFindDAO.java
|   |   |   |   |       +-- UserListDAO.java
|   |   |   |   |       +-- UserSaveDAO.java
|   |   |   |   +-- database/
|   |   |   |   |       +-- DatabaseConnection.java
|   |   |   |   |       +-- QueryDB.java
|   |   |   |   +-- network/                   # TCP Socket server
|   |   |   |   |   +-- NetworkServer.java     # Chấp nhận kết nối client
|   |   |   |   |   +-- ClientHandler.java     # Xử lý 1 client (1 thread)
|   |   |   |   |   +-- MessageRouter.java
|   |   |   |   |   \-- ImageHandler.java
|   |   |   |   +-- scheduler/                 # Đóng phiên hết giờ tự động
|   |   |   |   |   \-- AuctionScheduler.java
|   |   |   |   +-- service/                   # Business logic
|   |   |   |   |   +-- UserService.java
|   |   |   |   |   +-- BidService.java
|   |   |   |   |   +-- AutoBidService.java
|   |   |   |   |   +-- WalletService.java
|   |   |   |   |   +-- SnipeGuardService.java
|   |   |   |   |   +-- AdminService.java
|   |   |   |   |   \-- auction/
|   |   |   |   |       +-- BidPlacementService.java   # Core: thread-safe bidding
|   |   |   |   |       +-- AuctionProductService.java
|   |   |   |   |       \-- AuctionQueryService.java
|   |   |   |   \-- autobid/                   # Auto-bid Event Bus
|   |   |   |       +-- BidAuto.java           # Singleton Producer-Consumer
|   |   |   |       \-- RecordBid.java
|   |   |   |
|   |   |   \-- shared/                        # Code dùng chung client & server
|   |   |       +-- exception/                 # Custom exception hierarchy
|   |   |       +-- model/Entity/              # Domain models
|   |   |       |   +-- User/  (User, Bidder, Seller, Admin)
|   |   |       |   +-- Item/  (Item, Art, Electronics, Vehicle)
|   |   |       |   \-- Auction_Bid/ (Auction, Bid, AuctionStatus)
|   |   |       \-- pattern/                   # Design patterns
|   |   |           +-- AuctionManager.java    # Singleton
|   |   |           +-- ItemFactory.java       # Factory Method
|   |   |           \-- observer/              # Observer Pattern
|   |   |               +-- AuctionObserver.java
|   |   |               +-- AuctionSubject.java
|   |   |               \-- BidEvent.java
|   |   |
|   |   \-- resources/com/auction/client/view/
|   |       +-- fxml/                          # Giao diện FXML
|   |       |   +-- login-view.fxml
|   |       |   +-- home-view.fxml
|   |       |   +-- detail-view.fxml
|   |       |   +-- live-bidding-view.fxml
|   |       |   +-- manage-product-view.fxml
|   |       |   +-- admin-view.fxml
|   |       |   +-- bid-history-view.fxml
|   |       |   \-- profile-view.fxml
|   |       \-- css/style.css
|   |
|   \-- test/java/com/auction/                 # JUnit 5 test classes
|       +-- shared/pattern/observer/ObserverPatternTest.java
|       +-- shared/model/Entity/Auction_Bid/AuctionTest.java
|       +-- shared/model/Entity/User/UserTest.java
|       +-- shared/model/Entity/Item/ItemTest.java
|       +-- server/service/UserServiceTest.java
|       +-- server/service/AuctionServiceTest.java
|       \-- server/service/SnipeGuardServiceTest.java
|
+-- database.sql                               # Schema + dữ liệu mẫu
+-- pom.xml                                    # Maven build config
+-- .github/workflows/ci-cd.yml               # GitHub Actions CI/CD
\-- README.md
```

# 4. Yêu cầu cài đặt

- Bước 1 — Cài Java JDK 21

Windows:

```
Tải từ: https://adoptium.net/temurin/releases/?version=21
Cài đặt file .msi, sau đó kiểm tra:
> java -version
```

macOS:

```
# Dùng Homebrew
brew install --cask temurin@21

# Kiểm tra
java -version
```

Linux (Ubuntu/Debian):

```
sudo apt update
sudo apt install -y temurin-21-jdk
# hoặc:
sudo apt install -y openjdk-21-jdk

# Kiểm tra
java -version
```

- Bước 2 — Cài Maven 3.9+

Windows:

```
Tải từ: https://maven.apache.org/download.cgi
Giải nén và thêm vào PATH.
Kiểm tra: mvn -version
```

macOS:

```
brew install maven
mvn -version
```

Linux (Ubuntu/Debian):

```
sudo apt install -y maven
mvn -version
```

- Bước 3 — Cài MySQL (nếu chạy DB local)
```
Bỏ qua bước này nếu dùng kết nối cloud đã có sẵn trong source code.
```

Windows / macOS: Tải MySQL Community Server từ https://dev.mysql.com/downloads/

Linux (Ubuntu/Debian):

```
sudo apt install -y mysql-server
sudo systemctl start mysql
sudo mysql_secure_installation
```

# 5. Hướng dẫn chạy chương trình

- Bước 1 — Clone repository

```
git clone [LINK_GITHUB_REPO]
cd giau-da-project-nhom7
```

- Bước 2 — Thiết lập Database
```
Nếu dự án đã cấu hình kết nối cloud trong DatabaseConnection.java, bỏ qua bước này.
```

Mở MySQL CLI hoặc MySQL Workbench và chạy:

```
# Từ terminal (Linux/macOS)
mysql -u root -p < database.sql

# Từ terminal (Windows)
mysql -u root -p < database.sql

# Hoặc trong MySQL CLI:
# mysql> SOURCE /đường/dẫn/tới/database.sql;
```

- Bước 3 — Build project

```
# Tất cả hệ điều hành
mvn clean compile
```

- Bước 4 — Chạy Server (Terminal / Command Prompt thứ nhất)
```
Phải chạy Server trước, sau đó mới chạy Client.
```

Windows (Command Prompt / PowerShell):

```
mvn exec:java -Dexec.mainClass="com.auction.server.Main"
```

macOS / Linux (Terminal):

```
mvn exec:java -Dexec.mainClass="com.auction.server.Main"
```

Server khởi động thành công khi thấy:

```
[Server] Đang khởi động trên port 9090
[Scheduler] Đã khởi động. Kiểm tra phiên hết giờ mỗi 10 giây.
```

- Bước 5 — Chạy Client (Terminal / Command Prompt thứ hai)

Windows:

```
mvn exec:java -Dexec.mainClass="com.auction.client.Launcher"
```

macOS / Linux:

```
mvn exec:java -Dexec.mainClass="com.auction.client.Launcher"
```

Cửa sổ giao diện THE CURATOR sẽ xuất hiện. Client tự động kết nối đến Server tại ```localhost:9090```.

- Bước 6 — Chạy nhiều Client đồng thời (tùy chọn)

Mở thêm Terminal mới và chạy lại lệnh ở Bước 5. Mỗi lần chạy sẽ mở thêm một cửa sổ client độc lập — dùng để test realtime bid update giữa nhiều người dùng.

Chạy Unit Tests

```
mvn test
```

Kết quả test hiển thị trong terminal. Report chi tiết tại target/surefire-reports/.


Tài khoản test mặc định:

- Username: ```admin```. Password: ```admin```. Role: ```Admin```
- Username: ```seller1```. Password: ```sell123```. Role: ```Seller```
- Username: ```minh_dz```. Password: ```123456```. Role: ```Bidder```
- Username: ```test_user```. Password: ```1111```. Role: ```Bidder```

# 6. Danh sách chức năng đã hoàn thành

- Xác thực & Người dùng

 Đăng ký tài khoản (Bidder / Seller)
 
 Đăng nhập với phân quyền theo role
 
 Validate đầu vào phía client và server
 
 Tự động chuyển hướng đúng màn hình theo role sau khi đăng nhập
 

- Bidder

Xem danh sách phiên đấu giá đang diễn ra (realtime refresh)

Tìm kiếm phiên theo tên sản phẩm

Xem chi tiết sản phẩm với đồng hồ đếm ngược realtime

Tham gia đặt giá trong phòng Live Bidding

Nhận cập nhật giá realtime từ các bidder khác (không cần refresh)

Xem biểu đồ diễn biến giá realtime (Line Chart)

Xem bảng lịch sử tất cả lượt bid trong phiên

Kích hoạt Auto-Bid với giá tối đa và bước giá tùy chỉnh

Nhận Toast Notification khi có bid mới

Xem lịch sử đấu giá cá nhân (thắng / thua / đang diễn ra)

Lọc lịch sử theo trạng thái và tìm kiếm theo tên sản phẩm


- Seller

Đăng ký sản phẩm mới (Art / Electronics / Vehicle)

Upload ảnh sản phẩm lên cloud (ImgBB API)

Chỉnh sửa thông tin sản phẩm (tên, giá, bước giá, mô tả, thời gian)

Xóa sản phẩm (cascade xóa bid, auction liên quan)

Xem danh sách sản phẩm đã đăng và trạng thái từng phiên


- Admin

Dashboard với thống kê tổng quan (tổng sản phẩm, người dùng, phiên)

Xem và tìm kiếm toàn bộ sản phẩm trên hệ thống

Xóa sản phẩm bất kỳ

Xem và tìm kiếm toàn bộ tài khoản người dùng

Xóa tài khoản người dùng (có xác nhận, không tự xóa mình)

Xem tất cả phiên đấu giá với bộ lọc (Tất cả / Đang diễn ra / Sắp diễn ra / Đã kết thúc)

Đóng phiên đấu giá bất kỳ

Tự động chuyển hướng vào Admin Panel sau khi đăng nhập


- Tính năng hệ thống nâng cao

🛡️ Anti-Sniping: Tự động gia hạn phiên +60 giây khi có bid trong 60 giây cuối

🤖 Auto-Bid Event Bus: BidAuto chạy trên single worker thread (Producer-Consumer), tự động phản đòn khi đối thủ vượt giá

📊 Biểu đồ giá realtime — Line chart cập nhật động theo từng lượt bid

🔔 Toast Notification — Thông báo popup khi có bid mới


- Unit Tests:

Tests được viết bằng JUnit 5, bao phủ logic quan trọng không cần kết nối DB:

Test File: ```ObserverPatternTest```. Phạm vi kiểm tra: ```Subscribe/Unsubscribe, notify bid placed, notify auction closed, isolation khi observer throw exception, BidEvent getters```

Test File: ```AuctionTest```. Phạm vi kiểm tra: ```Auction status (OPEN/RUNNING/FINISHED), isActive(), refreshStatus(), setStatus(String), AuctionManager Singleton, add/remove/findById, getActiveAuctions```

Test File: ```UserTest```. Phạm vi kiểm tra: ```getRole() cho Bidder/Seller/Admin, checkPassword(), setBalance() với giá trị âm, Polymorphism qua User reference```

Test File: ```ItemTest```. Phạm vi kiểm tra: ```getType() và showDetails() cho Art / Electronics / Vehicle, ItemFactory tạo đúng subclass, ItemFactory với type null/invalid/lowercase```

Test File: ```UserServiceTest```. Phạm vi kiểm tra: ```Validate input đăng ký (null, rỗng, email sai, password ngắn), validate input đăng nhập (null, rỗng, blank)```

Test File: ```AuctionServiceTest```. Phạm vi kiểm tra: ```BidResult enum, BidOutcome getters, validateBid logic (giá hợp lệ/thấp/phiên đóng/hết giờ/null), Anti-sniping logic, Concurrent bidding với 50 threads```

Test File: ```SnipeGuardServiceTest```. Phạm vi kiểm tra: ```Bid trong/ngoài cửa sổ 60s, ranh giới chính xác 60/61s, cascade 3 lần gia hạn, SnipeGuardResult getters```

# 7. Báo cáo & Demo

Báo cáo PDF: https://drive.google.com/drive/u/0/folders/1jgxN3DuCKqgJ1EA9H_yHBVMss7CRsveH?fbclid=IwY2xjawSJPlpleHRuA2FlbQIxMABicmlkETFPbXlpMlh4eFlHb3BpYldyc3J0YwZhcHBfaWQQMjIyMDM5MTc4ODIwMDg5MgABHp3aGc9tpTVEKnclFmuugaFtHE0nkOJbTiPSlrnGvEVfV23he8_BBWOIznKQ_aem_B0EbBVSU-Tc-jJ5CUx98Nw

Video Demo: https://youtu.be/ljssCCQB0Dk?si=1nyFSGGF2k5-cduC

GitHub Repository: https://github.com/chuducminh-16/_giau-da-project-nhom7

Lưu ý kỹ thuật:

- Server phải chạy trước khi mở Client. Nếu Client không kết nối được, ứng dụng vẫn mở nhưng các thao tác gửi/nhận dữ liệu sẽ không hoạt động.

- Mặc định Server lắng nghe tại ```localhost:9090```. Để chạy trên máy khác trong cùng mạng LAN, sửa host trong ```NetworkClient.java``` và ```AuctionClientApp.java```.

- Kết nối DB mặc định trỏ tới MySQL trên Railway Cloud. Nếu muốn dùng DB local, cập nhật ```URL```, ```USER```, ```PASSWORD``` trong ```src/main/java/com/auction/server/database/DatabaseConnection.java.```


# MVC Structure:
Client (JavaFX):

- View    — FXML files (login-view, home-view, live-bidding-view, ...)
           + CSS styles
- Controller — *Controller.java (LoginController, HomeController, ...)
           + MessageHandler (tách xử lý mạng ra khỏi Controller)
- Model   — shared/model/Entity (User, Item, Auction, Bid)
           + session (UserSession, SelectedProductSession)
           
Server:

- Controller — UserController, AuctionRoomEngineController, ProductController, ...
- Service    — UserService, BidPlacementService, AuctionProductService, ...
- DAO        — UserFindDAO, BidDAO, AuctionDAO, ... (chỉ server truy cập DB)


# OOP Design:

- Class Hierarchy
```
Entity (Abstract)
├── User (Abstract)
│   ├── Bidder       — Người tham gia đấu giá, có balance
│   ├── Seller       — Người đăng sản phẩm, có rating
│   └── Admin        — Quản trị viên, có adminLevel
└── Item (Abstract)
    ├── Art          — Tác phẩm nghệ thuật (artist)
    ├── Electronics  — Thiết bị điện tử (warrantyPeriod)
    └── Vehicle      — Phương tiện (mileage)
```
- Auction             — Quản lý trung tâm một phiên đấu giá
- Bid                 — Một lượt đặt giá (id, itemId, bidderId, amount, timestamp)
- AuctionStatus       — Enum: OPEN → RUNNING → FINISHED → PAID / CANCELED

# OOP Principles Applied
Nguyên lý Encapsulation
+ Nơi áp dụng: tất cả fields private/protected, truy cập qua getter/setter. Bidder.setBalance() kiểm tra giá trị âm trước khi gán.

Nguyên lý Inheritance
+ Nơi áp dụng: Bidder, Seller, Admin kế thừa User; Art, Electronics, Vehicle kế thừa Item.

Nguyên lý Polymorphism
+ Nơi áp dụng: User.getRole(), Item.getType(), Item.showDetails() được override ở từng subclass. ItemFactory trả về Item nhưng tạo đúng subclass tương ứng.

Nguyên lý Abstraction 
+ Nơi áp dụng: User và Item là abstract class. AuctionObserver là interface. AuctionException là abstract base exception.


# Design Patterns:

1. Singleton Pattern:  Đảm bảo duy nhất một instance cho các thành phần quan trọng:

- AuctionManager: Quản lý toàn bộ danh sách phiên đấu giá đang hoạt động. Dùng Initialization-on-demand Holder — lazy, thread-safe, không cần synchronized.

- NetworkClient: Kết nối TCP Socket duy nhất từ client đến server, dùng chung cho tất cả Controller.

- UserSession: Lưu thông tin người dùng đang đăng nhập trên client.

- BidAuto: Event Bus xử lý Auto-bid. Dùng Holder pattern, chạy trên single Worker Thread.


2. Factory Method Pattern:  ItemFactory.createItem(type, ...) tạo đúng subclass Item dựa trên chuỗi type:
```
// Tự động tạo Art, Electronics, hoặc Vehicle tùy thuộc vào type:
Item item = ItemFactory.createItem("ELECTRONICS", id, name, price, endTime, sellerId, "24");
```
- Input: "ART" / "ELECTRONICS" / "VEHICLE" (case-insensitive)
- Output: instance đúng subclass tương ứng
- Throws IllegalArgumentException nếu type không hợp lệ


3. Observer Pattern:  Dùng để cập nhật realtime giá đấu cho tất cả client đang xem một phiên:
```
AuctionSubject (Abstract)           AuctionObserver (Interface)
├── subscribe(observer)             ├── onBidPlaced(BidEvent)
├── unsubscribe(observer)           └── onAuctionClosed(auctionId, winnerId, price)
├── notifyBidPlaced(event)    ←→    ClientHandler (implements AuctionObserver)
└── notifyAuctionClosed(...)
```
```
BidEvent (Immutable)
└── auctionId, productId, bid, newPrice, bidderName, timestamp
```
- CopyOnWriteArrayList đảm bảo thread-safe cho danh sách observer
- Mỗi notifyBidPlaced() chạy trong try-catch riêng — 1 observer lỗi không ảnh hưởng observer khác
- BidEvent là immutable — an toàn broadcast cho nhiều thread


4. Strategy / Template Method Pattern (Power-ups / DAO):  Tuy không đặt tên formal, hệ thống DAO áp dụng tư tưởng Strategy:

- UserFindDAO, UserSaveDAO, UserListDAO — phân tách trách nhiệm đọc/ghi/liệt kê
- ItemFindDAO, ItemSaveDAO, ItemListDAO — tương tự cho Item
- Dễ dàng thay thế implementation mà không ảnh hưởng tầng Service

# System Architecture:
Client–Server Architecture
```
┌─────────────────────────────────┐     TCP Socket      ┌──────────────────────────────────┐
│           CLIENT                │◄──────────────────► │           SERVER                 │
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
```

