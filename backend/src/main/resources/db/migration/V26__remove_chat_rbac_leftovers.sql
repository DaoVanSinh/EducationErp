-- Phân hệ 5 (AI Chatbot) từng seed cột search_vector (V26 cũ: add_course_search_vector) và quyền CHAT
-- (V27 cũ: seed_chat_rbac), rồi bị gỡ bỏ hoàn toàn khỏi codebase khi tính năng chuyển sang một
-- service Python riêng. Migration là append-only: xoá FILE không tự xoá SCHEMA/DỮ LIỆU đã áp vào
-- những database đã chạy hai migration đó trước khi chúng bị gỡ. Migration này dọn đúng phần đó,
-- đồng thời AN TOÀN trên một database CHƯA từng có V26/V27 cũ (IF EXISTS / WHERE không khớp dòng nào
-- đều là no-op).
--
-- Thứ tự: xoá dữ liệu quyền trước (permission_groups cascade xuống permission_group_items +
-- role_permission_groups - xem V2/V3), rồi mới gỡ cột/ index không ai dùng nữa.
DELETE FROM permission_groups WHERE id = '11111111-0000-0000-0000-000000000004';
DELETE FROM permissions WHERE resource = 'CHAT';

DROP INDEX IF EXISTS idx_courses_search_vector;
ALTER TABLE courses DROP COLUMN IF EXISTS search_vector;
