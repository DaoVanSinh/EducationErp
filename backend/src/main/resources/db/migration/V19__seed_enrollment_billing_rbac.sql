INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'ENROLLMENT', 'CREATE'),
    (gen_random_uuid(), 'ENROLLMENT', 'READ'),
    (gen_random_uuid(), 'ENROLLMENT', 'UPDATE'),
    (gen_random_uuid(), 'INVOICE', 'CREATE'),
    (gen_random_uuid(), 'INVOICE', 'READ'),
    (gen_random_uuid(), 'INVOICE', 'UPDATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới
-- phải tự thêm dòng gán vào đúng group đó, giống cách V10/V13/V14 đã làm.
-- Không seed ENROLLMENT:APPROVE / INVOICE:APPROVE: phân hệ này không có luồng duyệt (spec mục 7).
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource IN ('ENROLLMENT', 'INVOICE');
