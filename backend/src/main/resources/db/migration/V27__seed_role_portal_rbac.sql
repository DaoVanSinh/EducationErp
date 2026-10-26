-- Cổng theo vai trò (đề cương ĐATN mục "Mục tiêu": RBAC rõ ràng cho Admin/Teacher/Student).
-- TEACHING:READ - giáo viên xem các lớp mình được phân công dạy.
-- LEARNING:READ - học viên tự tra cứu lớp, lịch học, điểm, tiến trình và học phí của chính mình.
INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'TEACHING', 'READ'),
    (gen_random_uuid(), 'LEARNING', 'READ');

-- Giáo viên: thêm vào group riêng của TEACHER do V25 tạo ('...0003'), KHÔNG vào group "Tự phục vụ
-- cơ bản" ('...0002') vì group đó gắn cho cả STUDENT.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000003', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'TEACHING' AND p.action = 'READ';

-- Học viên: group riêng, chỉ gắn cho STUDENT.
INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000004', 'Học viên tự tra cứu',
     'Xem lớp, lịch học, điểm, tiến trình và học phí của chính mình, scope PERSONAL');

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000004', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'LEARNING' AND p.action = 'READ';

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000004');

-- ADMIN cố ý KHÔNG nhận hai quyền này: đây là màn hình "của tôi", admin không dạy lớp nào và không
-- học lớp nào, nhận vào chỉ làm hiện thêm hai mục menu rỗng.
