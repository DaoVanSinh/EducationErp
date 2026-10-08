-- Điểm thật thuộc modules.academic (spec mục 3): courses giữ CẤU HÌNH cột điểm, academic giữ ĐIỂM.
-- enrollment_id/course_grade_column_id/entered_by_account_id có FK mức DB để toàn vẹn dữ liệu,
-- nhưng KHÔNG có @ManyToOne tương ứng trong Java (rule #3: không quan hệ JPA xuyên module) -
-- mirror enrollments của V17.
CREATE TABLE grade_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    course_grade_column_id UUID NOT NULL REFERENCES course_grade_columns(id),
    -- NUMERIC(5,2) khớp course_grade_columns.max_score: điểm phải cùng thang với cột nó thuộc về.
    -- Khoảng hợp lệ [0, max_score] là luật theo từng cột nên không biểu diễn được bằng CHECK ở đây,
    -- chặn ở GradeRules.isScoreWithinRange (Task 9) và RecordGrade (Task 12).
    score NUMERIC(5,2) NOT NULL,
    entered_by_account_id UUID NOT NULL REFERENCES accounts(id),
    entered_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    -- Spec mục 6: một điểm cho một cột của một lượt ghi danh. UNIQUE ĐẦY ĐỦ (không PARTIAL như V17):
    -- không có trạng thái nào khiến một dòng điểm "ngừng tính", nhập lại là SỬA dòng cũ. Đây cũng là
    -- lớp chặn cuối cho race hai request cùng nhập điểm một cột.
    UNIQUE (enrollment_id, course_grade_column_id)
);

CREATE INDEX idx_grade_entries_enrollment ON grade_entries (enrollment_id);
CREATE INDEX idx_grade_entries_column ON grade_entries (course_grade_column_id);
