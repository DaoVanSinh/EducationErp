-- Vòng đời tài khoản, quản trị chi nhánh, thu chi toàn bộ
-- (docs/superpowers/specs/2026-11-05-tai-khoan-chi-nhanh-hoc-phi-design.md).

-- 1. Một email chỉ có một tài khoản chưa đóng. Tài khoản đã đóng giữ nguyên để tra cứu lịch sử.
ALTER TABLE accounts DROP CONSTRAINT accounts_email_key;
CREATE UNIQUE INDEX uq_accounts_email_not_closed ON accounts (email) WHERE status <> 'CLOSED';

CREATE TABLE account_branch_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    from_branch_id UUID REFERENCES branches(id),
    to_branch_id UUID NOT NULL REFERENCES branches(id),
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by_account_id UUID NOT NULL REFERENCES accounts(id)
);
CREATE INDEX idx_account_branch_history_account ON account_branch_history (account_id, changed_at);

-- 2. Hai vai trò mới. Quản trị chi nhánh có phạm vi BRANCH, use case lọc theo chi nhánh chủ quản.
INSERT INTO permission_groups (id, name, description) VALUES
    ('11111111-0000-0000-0000-000000000005', 'Quản trị chi nhánh',
     'Vận hành giáo viên, học viên, lớp, học phí và chi trả lương của chi nhánh mình, scope BRANCH');

INSERT INTO permission_group_items (id, permission_group_id, permission_id, scope)
SELECT gen_random_uuid(), '11111111-0000-0000-0000-000000000005', p.id, 'BRANCH'
FROM permissions p
WHERE (p.resource = 'ACCOUNT' AND p.action IN ('READ', 'UPDATE'))
   OR (p.resource IN ('TEACHER', 'STUDENT', 'CLASS', 'ENROLLMENT', 'INVOICE', 'GRADE', 'ASSIGNMENT')
       AND p.action IN ('CREATE', 'READ', 'UPDATE'))
   OR (p.resource = 'ATTENDANCE' AND p.action IN ('READ', 'UPDATE'))
   OR (p.resource IN ('COURSE', 'DASHBOARD') AND p.action = 'READ')
   OR (p.resource = 'PAYROLL' AND p.action IN ('READ', 'UPDATE'));

INSERT INTO roles (id, code, name, system_default) VALUES
    ('22222222-0000-0000-0000-000000000004', 'BRANCH_ADMIN', 'Quản trị chi nhánh', true),
    ('22222222-0000-0000-0000-000000000005', 'FORMER_STAFF', 'Đã nghỉ việc', true);

INSERT INTO role_permission_groups (role_id, permission_group_id) VALUES
    ('22222222-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000005'),
    ('22222222-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000002'),
    ('22222222-0000-0000-0000-000000000005', '11111111-0000-0000-0000-000000000002');

-- 3. Lịch sử giáo viên của lớp: buổi ghi bù sau khi thay giáo viên vẫn tính giờ đúng người đã dạy.
CREATE TABLE class_teacher_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    teacher_id UUID NOT NULL REFERENCES accounts(id),
    effective_from DATE NOT NULL,
    UNIQUE (class_id, effective_from)
);
INSERT INTO class_teacher_assignments (class_id, teacher_id, effective_from)
SELECT id, teacher_id, start_date FROM classes;

-- 4. Phiếu lương thuộc chi nhánh chủ quản của giáo viên tại thời điểm tính, chi trả đủ một lần.
ALTER TABLE payslips
    ADD COLUMN branch_id UUID REFERENCES branches(id),
    ADD COLUMN paid_on DATE,
    ADD COLUMN payment_method VARCHAR(16),
    ADD COLUMN paid_by_account_id UUID REFERENCES accounts(id),
    ADD COLUMN payment_note VARCHAR(500);
UPDATE payslips p SET branch_id = a.home_branch_id FROM accounts a WHERE a.id = p.account_id;
CREATE INDEX idx_payslips_branch ON payslips (branch_id);

-- 5. Gia hạn học phí có lý do. Hoá đơn đã gia hạn không bị đổi hạn khi đổi ngày khai giảng.
ALTER TABLE invoices
    ADD COLUMN due_extended BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN extension_reason VARCHAR(500);

-- 6. Ghi danh đang học chưa có hoá đơn: lập bù đủ học phí, hạn là ngày thứ 7 tính từ khai giảng.
-- Khoá chưa có học phí và ghi danh trong combo không lập được ở đây.
INSERT INTO invoices (enrollment_id, student_profile_id, course_id, branch_id, installment_number, amount, amount_paid,
                      status, due_date, issued_at, created_by_account_id)
SELECT e.id, e.student_profile_id, e.course_id, e.branch_id, 1, COALESCE(m.discounted_tuition_fee, co.tuition_fee), 0,
       'UNPAID', cl.start_date + 6, now(), e.created_by_account_id
FROM enrollments e
JOIN classes cl ON cl.id = e.class_id
JOIN courses co ON co.id = e.course_id
LEFT JOIN group_registration_members m ON m.enrollment_id = e.id
WHERE e.status = 'ACTIVE'
  AND COALESCE(m.discounted_tuition_fee, co.tuition_fee) IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.enrollment_id = e.id AND i.status <> 'CANCELLED')
  AND NOT EXISTS (SELECT 1 FROM combo_enrollments ce WHERE ce.enrollment_id = e.id);
