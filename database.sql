-- 1. Tạo một cái thùng chứa tên là auction_db
CREATE DATABASE IF NOT EXISTS auction_db;

-- 2. Nhảy vào trong cái thùng đó để làm việc
USE auction_db;

-- 3. Xây bảng Người dùng (Lưu ID, Tên, Pass, Tiền)
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(50) NOT NULL,
    balance DECIMAL(15, 2) DEFAULT 1000.00
);

-- 4. Xây bảng Sản phẩm (Lưu ID, Tên, Giá, Ngày hết hạn)
CREATE TABLE items (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    current_price DECIMAL(15, 2) NOT NULL,
    end_time DATETIME NOT NULL,
    type VARCHAR(50) NOT NULL
);
USE auction_db;
-- Bơm thử 2 thằng User
INSERT INTO users (id, username, password, balance) VALUES 
('U01', 'minh_dz', '123456', 5000.00),
('U02', 'test_user', '1111', 2000.00);

-- Bơm thử 2 món hàng
INSERT INTO items (id, name, current_price, end_time, type) VALUES 
('I01', 'Macbook Pro M3', 2000.00, '2026-12-31 23:59:59', 'ELECTRONICS'),
('I02', 'Mô hình Gundam', 50.00, '2026-10-20 12:00:00', 'ART');
SELECT * FROM users limit 5;
USE auction_db;

-- =====================================
-- 4. TẠO BẢNG LỊCH SỬ GIAO DỊCH (TRANSACTIONS)
-- =====================================
CREATE TABLE transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    item_id VARCHAR(50),
    winner_id VARCHAR(50),
    final_price DECIMAL(15, 2) NOT NULL,
    transaction_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id),
    FOREIGN KEY (winner_id) REFERENCES users(id)
);
-- TẠO THỬ LỊCH SỬ GIAO DỊCH--
INSERT INTO transactions(item_id, winner_id, final_price, transaction_time) VALUES ('I01', 'U01', 2500.00, '2026-04-13 12:00:00')


SELECT * FROM transactions;