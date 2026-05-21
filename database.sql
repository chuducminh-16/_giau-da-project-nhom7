-- =====================================
-- 1. XÓA BẢNG CŨ (con trước, cha sau)
-- =====================================
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS bids;
DROP TABLE IF EXISTS auctions;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS users;

CREATE DATABASE IF NOT EXISTS auction_db;
USE auction_db;

-- =====================================
-- 2. BẢNG USERS
-- =====================================
CREATE TABLE users (
    id          VARCHAR(50)    PRIMARY KEY,
    username    VARCHAR(50)    UNIQUE NOT NULL,
    email       VARCHAR(100)   UNIQUE,
    password    VARCHAR(50)    NOT NULL,
    balance     DECIMAL(15, 2) DEFAULT 1000.00,
    role        VARCHAR(20)    DEFAULT 'BIDDER',
    rating      DECIMAL(3, 1)  DEFAULT 5.0,
    admin_level INT            DEFAULT 1
);

-- =====================================
-- 3. BẢNG ITEMS
-- =====================================
CREATE TABLE items (
    id            VARCHAR(50)    PRIMARY KEY,
    name          VARCHAR(100)   NOT NULL,
    current_price DECIMAL(15, 2) NOT NULL,
    end_time      DATETIME       NOT NULL,
    type          VARCHAR(50)    NOT NULL,
    seller_id     VARCHAR(50),
    status        VARCHAR(20)    DEFAULT 'OPEN',
    FOREIGN KEY (seller_id) REFERENCES users(id)
);

-- =====================================
-- 4. BẢNG AUCTIONS
-- =====================================
CREATE TABLE auctions (
    id            BIGINT         PRIMARY KEY,
    item_id       VARCHAR(50)    NOT NULL,
    seller_id     VARCHAR(50)    NOT NULL,
    current_price DECIMAL(15, 2) NOT NULL,
    status        VARCHAR(20)    DEFAULT 'OPEN',  -- OPEN / RUNNING / FINISHED / PAID / CANCELED
    end_time      DATETIME       NOT NULL,
    created_at    DATETIME       DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id)   REFERENCES items(id),
    FOREIGN KEY (seller_id) REFERENCES users(id)
);

-- =====================================
-- 5. BẢNG BIDS
-- =====================================
CREATE TABLE bids (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    item_id   VARCHAR(50)    NOT NULL,
    bidder_id VARCHAR(50)    NOT NULL,
    bid_price DECIMAL(15, 2) NOT NULL,
    bid_time  DATETIME       DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id)   REFERENCES items(id),
    FOREIGN KEY (bidder_id) REFERENCES users(id)
);

-- =====================================
-- 6. BẢNG TRANSACTIONS
-- =====================================
CREATE TABLE transactions (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    item_id          VARCHAR(50),
    winner_id        VARCHAR(50),
    final_price      DECIMAL(15, 2) NOT NULL,
    transaction_time DATETIME       DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id)   REFERENCES items(id),
    FOREIGN KEY (winner_id) REFERENCES users(id)
);

-- =====================================
-- 7. DỮ LIỆU MẪU (thứ tự: cha trước, con sau)
-- =====================================

-- 7.1 Users (không phụ thuộc gì)
INSERT INTO users (id, username, email, password, balance, role, rating, admin_level) VALUES
('U01', 'minh_dz',   'minh@gmail.com',   '123456', 5000.00, 'BIDDER', 5.0, 1),
('U02', 'test_user', 'test@gmail.com',   '1111',   2000.00, 'BIDDER', 5.0, 1),
('U03', 'admin',     'admin@gmail.com',  'admin',  0.00,    'ADMIN',  5.0, 3),
('U04', 'seller1',   'seller@gmail.com', 'sell123',0.00,    'SELLER', 4.5, 1);

-- 7.2 Items (cần seller_id → users phải có trước)
INSERT INTO items (id, name, current_price, end_time, type, seller_id, status) VALUES
('I01', 'Macbook Pro M3', 2000.00, '2026-12-31 23:59:59', 'ELECTRONICS', 'U04', 'OPEN'),
('I02', 'Mô hình Gundam',   50.00, '2026-10-20 12:00:00', 'ART',         'U04', 'OPEN');

-- 7.3 Auctions (cần item_id + seller_id → users + items phải có trước)
INSERT INTO auctions (id, item_id, seller_id, current_price, status, end_time) VALUES
(1001, 'I01', 'U04', 2000.00, 'RUNNING', '2026-12-31 23:59:59'),
(1002, 'I02', 'U04',   50.00, 'OPEN',    '2026-10-20 12:00:00');

-- 7.4 Bids (cần item_id + bidder_id)
INSERT INTO bids (item_id, bidder_id, bid_price) VALUES
('I01', 'U01', 2200.00),
('I01', 'U02', 2400.00),
('I01', 'U01', 2500.00);

-- 7.5 Transactions (cần item_id + winner_id — sau cùng)
INSERT INTO transactions (item_id, winner_id, final_price, transaction_time) VALUES
('I01', 'U01', 2500.00, '2026-04-13 12:00:00');

-- =====================================
-- 8. KIỂM TRA
-- =====================================
SELECT * FROM users;
SELECT * FROM items;
SELECT * FROM auctions;
SELECT * FROM bids;
SELECT * FROM transactions;
