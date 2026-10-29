-- Điểm danh theo buổi (đề cương ĐATN - Phân hệ Quản lý Học tập: "quản lý điểm danh"). Thuộc
-- modules.attendance. class_id/enrollment_id/created_by_account_id có FK mức DB để toàn vẹn dữ liệu,
-- nhưng KHÔNG có @ManyToOne tương ứng trong Java (rule #3: không quan hệ JPA xuyên module) - mirror
-- grade_entries của V24.
CREATE TABLE attendance_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id),
    session_date DATE NOT NULL,
    note VARCHAR(500),
    created_by_account_id UUID NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- Một lớp một buổi điểm danh mỗi ngày: điểm danh lại cùng ngày là SỬA buổi cũ, và đây cũng là
    -- lớp chặn cuối cho race hai giáo viên/admin cùng tạo buổi một lúc.
    UNIQUE (class_id, session_date)
);

CREATE INDEX idx_attendance_sessions_class ON attendance_sessions (class_id);

CREATE TABLE attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    -- PRESENT / LATE / ABSENT / EXCUSED (AttendanceConstants.AttendanceStatus).
    status VARCHAR(16) NOT NULL,
    note VARCHAR(500),
    UNIQUE (session_id, enrollment_id)
);

CREATE INDEX idx_attendance_records_enrollment ON attendance_records (enrollment_id);
