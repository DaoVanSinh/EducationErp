-- Quyền cho điểm danh, bài tập/mini-test, mini-task (V28, V29).
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'ATTENDANCE', 'READ'),
    (gen_random_uuid(), 'ATTENDANCE', 'UPDATE'),
    (gen_random_uuid(), 'ASSIGNMENT', 'CREATE'),
    (gen_random_uuid(), 'ASSIGNMENT', 'READ'),
    (gen_random_uuid(), 'ASSIGNMENT', 'UPDATE'),
    (gen_random_uuid(), 'PRACTICE', 'CREATE');

-- Admin ("Toàn quyền hệ thống"): mọi lớp, scope ORGANIZATION - giống cách V10/V13/V19/V25 đã làm.
-- KHÔNG gồm PRACTICE: làm mini-task là việc của học viên, admin không học lớp nào (mirror V27 không
-- cấp LEARNING cho admin).
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource IN ('ATTENDANCE', 'ASSIGNMENT');

-- Giáo viên: group riêng của TEACHER (V25, '...0003'), scope PERSONAL - chỉ lớp mình được phân công,
-- guard trong modules.attendance/modules.assessment so teacherId.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000003', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource IN ('ATTENDANCE', 'ASSIGNMENT');

-- Học viên: group "Học viên tự tra cứu" (V27, '...0004') - xem bài tập/điểm danh đã nằm trong
-- LEARNING:READ, ở đây chỉ thêm quyền nộp một lượt làm mini-task của chính mình.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000004', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'PRACTICE' AND p.action = 'CREATE';
