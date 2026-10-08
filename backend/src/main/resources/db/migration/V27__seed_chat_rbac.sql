INSERT INTO permissions (id, resource, action) VALUES
    (gen_random_uuid(), 'CHAT', 'CREATE');

-- V5 đã seed "Toàn quyền hệ thống" xong và đã chạy rồi - không sửa lại được, nên permission mới phải
-- tự thêm dòng gán vào đúng group đó, giống cách V10/V13/V14/V19/V25 đã làm. Admin cần quyền này để
-- kiểm thử/hỗ trợ học viên (spec mục 1).
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000001', p.id, 'ORGANIZATION'
FROM permissions p WHERE p.resource = 'CHAT';

-- Mirror V25: KHÔNG tái dùng group "Tự phục vụ cơ bản" ('...0002'). V5 gắn group đó cho CẢ role
-- TEACHER ('...0002') VÀ role STUDENT ('...0003') trong role_permission_groups, nên nhét CHAT vào đó
-- là cấp luôn cho giáo viên - trái spec mục 1 ("Giáo viên KHÔNG được cấp quyền - roadmap không nhắc
-- tới giáo viên dùng tính năng này"). Vì vậy: group RIÊNG, chỉ gắn cho STUDENT.
-- ChatRbacSeedIT.teacherGetsNoChatPermissionAtAll chốt lại điều này.
-- Id '...0004' còn trống: V5 dùng ...0001/...0002, V25 dùng ...0003.
INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000004', 'Học viên hỏi chatbot',
     'Gửi câu hỏi cho chatbot tư vấn khóa học, scope PERSONAL');

-- Scope PERSONAL là ĐỦ, và đó là chủ đích: AccessConstants.AccessRules.CREATE_CHAT cố ý KHÔNG mang
-- marker '@ORGANIZATION', nên ScopedPermissionEvaluator chỉ hỏi "có quyền CHAT:CREATE ở bất kỳ scope
-- nào không". Ở đây không có khái niệm sở hữu tài nguyên để kiểm thêm như *_GRADE của Phân hệ 4.
INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000004', p.id, 'PERSONAL'
FROM permissions p WHERE p.resource = 'CHAT';

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000004'); -- STUDENT
