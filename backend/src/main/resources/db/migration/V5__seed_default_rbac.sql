INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'ACCOUNT', 'CREATE'),
    (gen_random_uuid(), 'ACCOUNT', 'READ'),
    (gen_random_uuid(), 'ACCOUNT', 'UPDATE'),
    (gen_random_uuid(), 'ACCOUNT', 'DELETE'),
    (gen_random_uuid(), 'ROLE', 'CREATE'),
    (gen_random_uuid(), 'ROLE', 'READ'),
    (gen_random_uuid(), 'ROLE', 'UPDATE'),
    (gen_random_uuid(), 'ROLE', 'DELETE'),
    (gen_random_uuid(), 'GROUP', 'CREATE'),
    (gen_random_uuid(), 'GROUP', 'READ'),
    (gen_random_uuid(), 'GROUP', 'UPDATE'),
    (gen_random_uuid(), 'GROUP', 'DELETE'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'CREATE'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'READ'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'UPDATE'),
    (gen_random_uuid(), 'PERMISSION_GROUP', 'DELETE'),
    (gen_random_uuid(), 'BRANCH', 'CREATE'),
    (gen_random_uuid(), 'BRANCH', 'READ'),
    (gen_random_uuid(), 'BRANCH', 'UPDATE'),
    (gen_random_uuid(), 'AUDIT_LOG', 'READ'),
    (gen_random_uuid(), 'DASHBOARD', 'READ');

INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000001', 'Toàn quyền hệ thống', 'Mọi permission, scope ORGANIZATION'),
    ('11111111-0000-0000-0000-000000000002', 'Tự phục vụ cơ bản', 'Xem/sửa hồ sơ của chính mình');

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p;

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000002', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'ACCOUNT' AND p.action IN ('READ', 'UPDATE');

INSERT INTO roles (id, code, name, system_default) VALUES
    ('22222222-0000-0000-0000-000000000001', 'ADMIN', 'Quản trị viên', true),
    ('22222222-0000-0000-0000-000000000002', 'TEACHER', 'Giáo viên', true),
    ('22222222-0000-0000-0000-000000000003', 'STUDENT', 'Học viên', true);

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001'),
    ('22222222-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000002'),
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002');
