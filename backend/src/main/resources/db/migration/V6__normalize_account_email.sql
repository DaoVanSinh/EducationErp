-- Cột email lưu nguyên dạng người dùng gõ, và Postgres so sánh VARCHAR có phân biệt hoa thường,
-- nên UNIQUE ở V4 không chặn được 'a@x.com' và 'A@x.com' cùng tồn tại — hai tài khoản cho một
-- người. Đồng thời đăng nhập gõ khác hoa thường so với lúc tạo sẽ không tìm thấy tài khoản.
--
-- Chuẩn hoá dữ liệu cũ, rồi để DB tự canh bất biến: một đường ghi mới lỡ không đi qua
-- EmailNormalizer sẽ lỗi ngay tại đây, thay vì âm thầm tạo tài khoản trùng.
--
-- Nếu UPDATE này lỗi trùng khoá trên một DB đã có dữ liệu, tức là DB đó thật sự đang có hai tài
-- khoản chỉ khác hoa thường. Đó là việc phải xử lý tay (gộp hay xoá cái nào là quyết định nghiệp
-- vụ), không được tự chọn hộ ở migration.
UPDATE accounts SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));

ALTER TABLE accounts
    ADD CONSTRAINT accounts_email_normalized CHECK (email = lower(btrim(email)));
