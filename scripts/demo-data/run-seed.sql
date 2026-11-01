-- Nạp lại dữ liệu demo trong MỘT transaction: xoá bộ demo cũ rồi tạo lại. Lỗi ở bất kỳ bước nào thì
-- rollback toàn bộ, DB giữ nguyên như trước khi chạy. Cần biến psql: -v mailbox=ten@gmail.com
\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
SET client_min_messages = warning;
BEGIN;
\ir cleanup-demo.sql
\ir seed-demo.sql
COMMIT;
