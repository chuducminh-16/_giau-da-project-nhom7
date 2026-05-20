AuctionException  ← lớp cha, catch tổng quát
├── InvalidBidException          — giá đặt thấp / bằng giá hiện tại
├── AuctionClosedException       — phiên FINISHED/CANCELED/PAID
├── ProductNotFoundException     — sản phẩm không tồn tại
├── UserNotFoundException        — email sai / user không tồn tại
├── DuplicateAccountException    — email/username đã dùng khi đăng ký
├── UnauthorizedActionException  — không có quyền (sửa sp của người khác, seller tự bid...)
├── InvalidProductDataException  — tên rỗng, giá âm, endTime đã qua
├── DatabaseException            — wrap SQLException → lỗi dữ liệu
└── NetworkException             — lỗi kết nối socket/server