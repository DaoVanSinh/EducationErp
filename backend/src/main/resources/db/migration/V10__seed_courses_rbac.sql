INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'COURSE', 'CREATE'),
    (gen_random_uuid(), 'COURSE', 'READ'),
    (gen_random_uuid(), 'COURSE', 'UPDATE'),
    (gen_random_uuid(), 'CLASS', 'CREATE'),
    (gen_random_uuid(), 'CLASS', 'READ'),
    (gen_random_uuid(), 'CLASS', 'UPDATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới
-- phải tự thêm dòng gán vào đúng group đó, giống cách group này được build ban đầu.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource IN ('COURSE', 'CLASS');
