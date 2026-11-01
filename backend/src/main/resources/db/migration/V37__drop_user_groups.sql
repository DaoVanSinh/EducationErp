-- Gỡ tính năng "Nhóm người dùng" (Group): chưa có màn tạo nhóm, chưa từng có dữ liệu, và chưa có nhu cầu
-- nghiệp vụ rõ ràng. Quyền hiệu lực từ nay chỉ tính từ vai trò (role -> nhóm quyền -> quyền).
-- Thứ tự xoá: bảng nối trước, bảng gốc sau.
DROP TABLE IF EXISTS account_groups;
DROP TABLE IF EXISTS group_permission_groups;
DROP TABLE IF EXISTS user_groups;

-- Quyền trên tài nguyên GROUP không còn tài nguyên nào để bảo vệ. permission_group_items có
-- ON DELETE CASCADE nên các nhóm quyền đang chứa chúng tự bỏ theo.
DELETE FROM permissions WHERE resource = 'GROUP';
