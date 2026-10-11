-- Cấu hình cột điểm thuộc KHÓA HỌC, không thuộc lớp (spec mục 2 quyết định 2): mọi lớp cùng khóa
-- dùng chung một cấu trúc điểm. Mirror class_schedules của V9 về mọi mặt: con cascade của bảng cha,
-- ON DELETE CASCADE, một index theo khóa ngoại.
CREATE TABLE course_grade_columns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    -- NUMERIC(5,2): % trong điểm tổng kết. Ràng buộc "tổng = 100" là luật nghiệp vụ theo từng khóa
    -- học nên không biểu diễn được bằng CHECK trên một dòng - chặn ở GradeColumnRules (Task 2).
    weight_percent NUMERIC(5,2) NOT NULL,
    -- Thang điểm của riêng cột này (ví dụ 10): điểm tổng kết là Σ(score / max_score * weight_percent).
    max_score NUMERIC(5,2) NOT NULL,
    display_order INT NOT NULL
);

CREATE INDEX idx_course_grade_columns_course ON course_grade_columns (course_id);
