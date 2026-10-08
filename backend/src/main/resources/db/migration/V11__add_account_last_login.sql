ALTER TABLE accounts ADD COLUMN last_login TIMESTAMPTZ;

-- Tại thời điểm migration này chạy, luồng mời qua email (invite) chưa từng tồn tại - mọi account đã
-- có sẵn trong bảng chắc chắn đã từng đăng nhập bình thường trước đó (không có cổng lastLogin==null
-- nào để bị kẹt lại). Không backfill thì ngay lần triển khai migration này lên môi trường đã có dữ
-- liệu, mọi tài khoản đang hoạt động sẽ bị khoá ngoài hệ thống vì bị coi là "lần đăng nhập đầu tiên".
UPDATE accounts SET last_login = now() WHERE last_login IS NULL;
