-- Gỡ toàn bộ dữ liệu demo trong MỘT transaction.
\set ON_ERROR_STOP on
SET client_encoding = 'UTF8';
SET client_min_messages = warning;
BEGIN;
\ir cleanup-demo.sql
COMMIT;
SELECT 'Đã xoá dữ liệu demo' AS "Kết quả";
