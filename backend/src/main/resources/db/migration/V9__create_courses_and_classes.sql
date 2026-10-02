CREATE TABLE courses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    standard_session_count INT,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE classes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES courses(id),
    code VARCHAR(32) NOT NULL UNIQUE,
    -- branch_id/teacher_id có FK mức DB để toàn vẹn dữ liệu, giống accounts.home_branch_id - nhưng
    -- không có quan hệ JPA tương ứng trong Java (rule #3: không @ManyToOne xuyên module).
    branch_id UUID NOT NULL REFERENCES branches(id),
    teacher_id UUID NOT NULL REFERENCES accounts(id),
    max_seats INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX idx_classes_course ON classes (course_id);
CREATE INDEX idx_classes_branch ON classes (branch_id);

CREATE TABLE class_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id) ON DELETE CASCADE,
    day_of_week VARCHAR(16) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
);

CREATE INDEX idx_class_schedules_class ON class_schedules (class_id);
