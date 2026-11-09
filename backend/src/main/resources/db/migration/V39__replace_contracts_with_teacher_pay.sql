-- Bỏ hợp đồng lao động: điều khoản lương chuyển về hồ sơ giáo viên, giờ dạy lấy từ điểm danh
-- (docs/superpowers/specs/2026-11-05-luong-giao-vien-design.md). Thứ tự quan trọng: chuyển dữ liệu từ
-- employment_contracts sang chỗ mới TRƯỚC khi xoá bảng.

-- 1. Điều khoản lương trên hồ sơ giáo viên. pay_type NULL = chưa thiết lập lương.
ALTER TABLE teacher_profiles
    ADD COLUMN pay_type VARCHAR(16),
    ADD COLUMN monthly_salary NUMERIC(14,2),
    ADD COLUMN hourly_rate NUMERIC(14,2),
    ADD COLUMN work_start_date DATE,
    ADD COLUMN probation_end_date DATE,
    ADD COLUMN dependent_count INT NOT NULL DEFAULT 0,
    ADD COLUMN deactivated_on DATE;

CREATE TABLE teacher_allowances (
    teacher_profile_id UUID NOT NULL REFERENCES teacher_profiles(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    amount NUMERIC(14,2) NOT NULL
);
CREATE INDEX idx_teacher_allowances_profile ON teacher_allowances (teacher_profile_id);

-- Một tài khoản chỉ có tối đa một hợp đồng ACTIVE (use case cũ chặn), DISTINCT ON để chắc chắn.
CREATE TEMP TABLE active_contracts ON COMMIT DROP AS
SELECT DISTINCT ON (account_id) *
FROM employment_contracts
WHERE status = 'ACTIVE'
ORDER BY account_id, start_date DESC;

UPDATE teacher_profiles t
SET pay_type = CASE c.contract_type WHEN 'OFFICIAL' THEN 'MONTHLY' ELSE 'HOURLY' END,
    monthly_salary = CASE WHEN c.contract_type = 'OFFICIAL' THEN c.base_salary END,
    hourly_rate = CASE WHEN c.contract_type = 'COLLABORATOR' THEN c.hourly_rate END,
    work_start_date = c.start_date,
    probation_end_date = CASE WHEN c.contract_type = 'OFFICIAL' THEN c.probation_end_date END
FROM active_contracts c
WHERE c.account_id = t.account_id;

-- Phụ cấp chỉ áp dụng cho giáo viên trả theo tháng (đơn giá giờ đã bao gồm).
INSERT INTO teacher_allowances (teacher_profile_id, name, amount)
SELECT t.id, a.name, a.amount
FROM contract_allowances a
JOIN active_contracts c ON c.id = a.contract_id AND c.contract_type = 'OFFICIAL'
JOIN teacher_profiles t ON t.account_id = c.account_id;

-- Hồ sơ đã ngừng từ trước: không biết ngày ngừng thật, lấy hôm nay làm mốc.
UPDATE teacher_profiles SET deactivated_on = CURRENT_DATE WHERE NOT active;

-- 2. Ai dạy và dạy bao lâu được chụp vào buổi điểm danh: đổi lịch, đổi giáo viên về sau không làm đổi
-- buổi đã dạy. Buổi cũ lấy theo giáo viên và lịch hiện tại của lớp.
ALTER TABLE attendance_sessions
    ADD COLUMN teacher_account_id UUID REFERENCES accounts(id),
    ADD COLUMN duration_minutes INT;

UPDATE attendance_sessions s
SET teacher_account_id = c.teacher_id,
    duration_minutes = COALESCE(
        (SELECT SUM(EXTRACT(EPOCH FROM (cs.end_time - cs.start_time)) / 60)
         FROM class_schedules cs
         WHERE cs.class_id = s.class_id
           AND cs.day_of_week = (ARRAY['MON','TUE','WED','THU','FRI','SAT','SUN'])[EXTRACT(ISODOW FROM s.session_date)::int]),
        (SELECT MAX(EXTRACT(EPOCH FROM (cs.end_time - cs.start_time)) / 60)
         FROM class_schedules cs WHERE cs.class_id = s.class_id),
        0)
FROM classes c
WHERE c.id = s.class_id;

ALTER TABLE attendance_sessions
    ALTER COLUMN teacher_account_id SET NOT NULL,
    ALTER COLUMN duration_minutes SET NOT NULL;

CREATE INDEX idx_attendance_sessions_teacher_date ON attendance_sessions (teacher_account_id, session_date);

-- 3. Phiếu lương là ảnh chụp: tự chứa đủ số liệu để giải thích kết quả mà không cần hợp đồng.
ALTER TABLE payslips RENAME COLUMN contract_type TO pay_type;
UPDATE payslips SET pay_type = CASE pay_type WHEN 'OFFICIAL' THEN 'MONTHLY' ELSE 'HOURLY' END;

ALTER TABLE payslips
    ADD COLUMN pay_rate NUMERIC(14,2) NOT NULL DEFAULT 0,
    ADD COLUMN session_count INT NOT NULL DEFAULT 0,
    ADD COLUMN paid_days INT NOT NULL DEFAULT 0,
    ADD COLUMN probation_days INT NOT NULL DEFAULT 0,
    ADD COLUMN dependent_count INT NOT NULL DEFAULT 0,
    ADD COLUMN adjustment NUMERIC(14,2) NOT NULL DEFAULT 0;

-- Phiếu cũ: quy tắc cũ tính nguyên tháng, thử việc cũng nguyên tháng.
UPDATE payslips p
SET paid_days = d.days,
    probation_days = CASE WHEN p.in_probation THEN d.days ELSE 0 END
FROM (SELECT id, EXTRACT(DAY FROM (make_date(year, month, 1) + INTERVAL '1 month - 1 day'))::int AS days
      FROM payroll_runs) d
WHERE d.id = p.payroll_run_id AND p.pay_type = 'MONTHLY';

UPDATE payslips p
SET pay_rate = COALESCE(c.base_salary, c.hourly_rate, 0)
FROM (SELECT DISTINCT ON (account_id) account_id, base_salary, hourly_rate
      FROM employment_contracts ORDER BY account_id, start_date DESC) c
WHERE c.account_id = p.account_id;

ALTER TABLE payslips ADD CONSTRAINT uq_payslips_run_account UNIQUE (payroll_run_id, account_id);

-- Kỳ đã gửi duyệt hoặc đã duyệt mà không có phiếu nào (có từ trước khi chặn kỳ rỗng): mỗi tháng chỉ có một kỳ nên
-- kỳ rỗng này khoá vĩnh viễn tháng đó, không tính lương được nữa. Không có số liệu nào bị mất.
DELETE FROM payroll_runs r
WHERE r.status <> 'DRAFT'
  AND NOT EXISTS (SELECT 1 FROM payslips p WHERE p.payroll_run_id = r.id);

-- 4. Hợp đồng đã hết vai trò. File scan trong object storage không bị xoá theo.
DROP TABLE contract_allowances;
DROP TABLE employment_contracts;
