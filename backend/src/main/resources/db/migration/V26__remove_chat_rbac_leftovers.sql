-- Phân hệ 5 (AI Chatbot) từng seed quyền CHAT qua V26/V27, rồi bị gỡ bỏ hoàn toàn khỏi codebase
-- (bao gồm cả hai file migration đó) khi tính năng chuyển sang một service Python riêng. Migration
-- là append-only: xoá FILE không tự xoá DỮ LIỆU đã chèn vào những database đã chạy V27 trước khi nó
-- bị gỡ. Migration này dọn đúng phần dữ liệu đó, an toàn chạy lại trên một database CHƯA từng có
-- V26/V27 cũ (không có gì để xoá, hai lệnh DELETE dưới đây không khớp dòng nào).
--
-- Chỉ cần xoá permission_groups + permissions: permission_group_items.permission_group_id/
-- permission_id và role_permission_groups.permission_group_id đều REFERENCES ... ON DELETE CASCADE
-- (V2/V3), nên hai lệnh dưới tự dọn sạch mọi dòng phụ thuộc.
DELETE FROM permission_groups WHERE id = '11111111-0000-0000-0000-000000000004';
DELETE FROM permissions WHERE resource = 'CHAT';
