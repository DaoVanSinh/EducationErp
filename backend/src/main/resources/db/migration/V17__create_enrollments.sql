CREATE TABLE enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id),
    class_id UUID NOT NULL REFERENCES classes(id),
    -- course_id/branch_id là snapshot chốt lúc ghi danh (spec mục 4): lớp chuyển chi nhánh về sau
    -- không được làm đổi hoá đơn đã phát hành. FK mức DB để toàn vẹn dữ liệu, không có @ManyToOne
    -- tương ứng trong Java (rule #3: không quan hệ JPA xuyên module).
    course_id UUID NOT NULL REFERENCES courses(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    enrolled_at TIMESTAMPTZ NOT NULL,
    withdrawn_at TIMESTAMPTZ,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id)
);

CREATE INDEX idx_enrollments_student ON enrollments (student_profile_id);
CREATE INDEX idx_enrollments_class ON enrollments (class_id);

-- Review Focus #1: chặn ghi danh trùng ở tầng DB, không chỉ tầng application (defense in depth).
-- PARTIAL (WHERE status = 'ACTIVE', cú pháp Postgres) để học viên đã rút vẫn ghi danh lại được cùng
-- lớp đó - một unique index đầy đủ sẽ chặn sai trường hợp hợp lệ này.
CREATE UNIQUE INDEX uq_enrollments_active_student_class
    ON enrollments (student_profile_id, class_id)
    WHERE status = 'ACTIVE';
