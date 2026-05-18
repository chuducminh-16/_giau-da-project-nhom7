-- 1. Xóa bảng cũ (thứ tự: con trước, cha sau)
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS bids;
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
                       admin_level INT            DEFAULT 1,
    -- ── THÊM MỚI ──
                       full_name   VARCHAR(100),            -- tên đầy đủ từ form Register
                       phone       VARCHAR(20),             -- số điện thoại
                       address     TEXT                     -- địa chỉ
);

-- =====================================
-- 3. BẢNG ITEMS (Sản phẩm đấu giá)
-- =====================================
CREATE TABLE items (
                       id            VARCHAR(50)    PRIMARY KEY,
                       name          VARCHAR(100)   NOT NULL,
                       current_price DECIMAL(15, 2) NOT NULL,
                       end_time      DATETIME       NOT NULL,
                       type          VARCHAR(50)    NOT NULL,
                       seller_id     VARCHAR(50),
                       status        VARCHAR(20)    DEFAULT 'OPEN',
    -- ── THÊM MỚI ──
                       description   TEXT,                 -- mô tả sản phẩm từ ManageProduct
                       starting_price DECIMAL(15, 2),      -- giá khởi điểm gốc (không đổi)
                       bid_increment  DECIMAL(15, 2) DEFAULT 1.00, -- bước giá tối thiểu
                       current_bid    DECIMAL(15, 2) DEFAULT 0,    -- giá đặt cao nhất hiện tại
                       current_bidder VARCHAR(50),         -- userId đang dẫn đầu
                       image_path     VARCHAR(500),        -- đường dẫn ảnh sản phẩm
                       start_time     DATETIME,            -- thời gian bắt đầu phiên
                       created_at     DATETIME    DEFAULT CURRENT_TIMESTAMP,
                       FOREIGN KEY (seller_id)      REFERENCES users(id),
                       FOREIGN KEY (current_bidder) REFERENCES users(id)
);

-- =====================================
-- 4. BẢNG BIDS (Lịch sử đặt giá)
-- =====================================
CREATE TABLE bids (
    -- ── THÊM CỘT id dạng UUID (dùng cho BidDAO.save()) ──
                      id        VARCHAR(50)    PRIMARY KEY          DEFAULT (UUID()),
                      item_id   VARCHAR(50)    NOT NULL,
                      bidder_id VARCHAR(50)    NOT NULL,
                      bid_price DECIMAL(15, 2) NOT NULL,
                      bid_time  DATETIME       DEFAULT CURRENT_TIMESTAMP,
                      FOREIGN KEY (item_id)   REFERENCES items(id),
                      FOREIGN KEY (bidder_id) REFERENCES users(id)
);

-- =====================================
-- 5. BẢNG TRANSACTIONS (Giao dịch hoàn tất)
-- =====================================
CREATE TABLE transactions (
                              id               INT AUTO_INCREMENT PRIMARY KEY,
                              item_id          VARCHAR(50),
                              winner_id        VARCHAR(50),
                              final_price      DECIMAL(15, 2) NOT NULL,
                              transaction_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (item_id)   REFERENCES items(id),
                              FOREIGN KEY (winner_id) REFERENCES users(id)
);

-- =====================================
-- 6. DỮ LIỆU MẪU
-- =====================================
INSERT INTO users (id, username, email, password, balance,
                   role, rating, admin_level) VALUES
                                                  ('U01', 'minh_dz',   'minh@gmail.com',   '123456',  5000.00, 'BIDDER', 5.0, 1),
                                                  ('U02', 'test_user', 'test@gmail.com',   '1111',    2000.00, 'BIDDER', 5.0, 1),
                                                  ('U03', 'admin',     'admin@gmail.com',  'admin',      0.00, 'ADMIN',  5.0, 3),
                                                  ('U04', 'seller1',   'seller@gmail.com', 'sell123',    0.00, 'SELLER', 4.5, 1);

INSERT INTO items (id, name, current_price, starting_price,
                   bid_increment, end_time, type,
                   seller_id, status) VALUES
                                          ('I01', 'Macbook Pro M3', 2000.00, 2000.00, 100.00,
                                           '2026-12-31 23:59:59', 'ELECTRONICS', 'U03', 'OPEN'),
                                          ('I02', 'Mô hình Gundam',   50.00,   50.00,   5.00,
                                           '2026-10-20 12:00:00', 'ART',         'U03', 'OPEN');

INSERT INTO bids (item_id, bidder_id, bid_price) VALUES
                                                     ('I01', 'U01', 2200.00),
                                                     ('I01', 'U02', 2400.00),
                                                     ('I01', 'U01', 2500.00);

-- Cập nhật current_bid sau khi insert bids mẫu
UPDATE items SET current_bid = 2500.00, current_bidder = 'U01'
WHERE id = 'I01';

INSERT INTO transactions (item_id, winner_id, final_price,
                          transaction_time) VALUES
    ('I01', 'U01', 2500.00, '2026-04-13 12:00:00');

-- Tài khoản admin mặc định (password: admin123 — SHA-256)
INSERT INTO users (id, username, email, password, role)
VALUES ('admin_001', 'admin_curator', 'admin@curator.com',
        '240be518fabd2724ddb6f04eeb1da5967448d7e831d9a0', 'ADMIN');

-- =====================================
-- 7. KIỂM TRA
-- =====================================
SELECT * FROM users;
SELECT * FROM items;
SELECT * FROM bids;
SELECT * FROM transactions;