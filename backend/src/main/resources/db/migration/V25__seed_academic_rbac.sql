INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'GRADE', 'CREATE'),
    (gen_random_uuid(), 'GRADE', 'READ'),
    (gen_random_uuid(), 'GRADE', 'UPDATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới
-- phải tự thêm dòng gán vào đúng group đó, giống cách V10/V13/V14/V19 đã làm.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource = 'GRADE';

-- Đây là lần đầu một role khác ADMIN được cấp quyền cho một resource mới (spec mục 8), và nó KHÔNG
-- đi qua group "Tự phục vụ cơ bản" ('...0002') như ACCOUNT:READ/UPDATE:PERSONAL của V5: V5 gắn group
-- đó cho CẢ role TEACHER ('...0002') VÀ role STUDENT ('...0003') trong role_permission_groups, nên
-- nhét GRADE vào đó là cấp luôn cho học viên - trái spec mục 1 ("học viên KHÔNG tự xem được
-- điểm/tiến trình của mình qua hệ thống ở giai đoạn này"). Vì vậy: group RIÊNG, chỉ gắn cho TEACHER.
-- AcademicRbacSeedIT.studentGetsNoGradePermissionAtAll chốt lại điều này.
INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000003', 'Giáo viên nhập điểm',
     'Xem và nhập điểm cho lớp mình được phân công dạy, scope PERSONAL');

-- Chỉ READ + UPDATE: nhập điểm là upsert (UNIQUE enrollment+column của V24) nên đi theo UPDATE, và
-- CREATE ở đây sẽ là một quyền không endpoint nào dùng. CREATE chỉ nằm ở nhóm admin bên trên.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000003', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'GRADE' AND p.action IN ('READ', 'UPDATE');

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000003');
