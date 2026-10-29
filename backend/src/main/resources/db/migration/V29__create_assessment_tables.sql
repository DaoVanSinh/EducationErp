-- Bài tập / mini-test định kỳ và mini-task tự ôn luyện (đề cương ĐATN - Phân hệ Quản lý Học tập).
-- Thuộc modules.assessment. Tham chiếu sang classes/enrollments/accounts chỉ là FK mức DB, không có
-- @ManyToOne xuyên module (rule #3).

-- Bài tập / mini-test do giáo viên giao, làm ngoài hệ thống; hệ thống theo dõi tình trạng nộp và điểm.
CREATE TABLE assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id),
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    -- HOMEWORK / MINI_TEST (AssessmentConstants.AssignmentType).
    assignment_type VARCHAR(16) NOT NULL,
    due_date DATE,
    max_score NUMERIC(5,2) NOT NULL,
    -- Không có Actions.DELETE trong hệ thống: "xoá" là tắt active, mirror courses.active.
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_assignments_class ON assignments (class_id);

CREATE TABLE assignment_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    -- SUBMITTED / LATE / NOT_SUBMITTED (AssessmentConstants.SubmissionStatus).
    status VARCHAR(16) NOT NULL,
    -- Khoảng [0, max_score] là luật theo từng bài nên chặn ở AssessmentRules, không bằng CHECK.
    score NUMERIC(5,2),
    feedback VARCHAR(1000),
    updated_by_account_id UUID NOT NULL REFERENCES accounts(id),
    updated_at TIMESTAMPTZ NOT NULL,
    -- Một kết quả cho một học viên ở một bài: nhập lại là SỬA, mirror grade_entries (V24).
    UNIQUE (assignment_id, enrollment_id)
);

CREATE INDEX idx_assignment_results_enrollment ON assignment_results (enrollment_id);

-- Mini-task: bộ câu hỏi trắc nghiệm học viên tự làm trực tiếp trên hệ thống, chấm tự động, làm lại
-- không giới hạn để ôn luyện.
CREATE TABLE mini_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    class_id UUID NOT NULL REFERENCES classes(id),
    title VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mini_tasks_class ON mini_tasks (class_id);

CREATE TABLE mini_task_questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mini_task_id UUID NOT NULL REFERENCES mini_tasks(id) ON DELETE CASCADE,
    content VARCHAR(1000) NOT NULL,
    correct_option_index INT NOT NULL,
    display_order INT NOT NULL
);

CREATE INDEX idx_mini_task_questions_task ON mini_task_questions (mini_task_id);

CREATE TABLE mini_task_question_options (
    question_id UUID NOT NULL REFERENCES mini_task_questions(id) ON DELETE CASCADE,
    option_index INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    PRIMARY KEY (question_id, option_index)
);

-- Một lượt làm bài. Lưu số câu đúng/tổng số câu tại thời điểm nộp (snapshot): giáo viên sửa câu hỏi
-- về sau không làm đổi kết quả các lượt đã nộp.
CREATE TABLE mini_task_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mini_task_id UUID NOT NULL REFERENCES mini_tasks(id),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    correct_count INT NOT NULL,
    total_questions INT NOT NULL,
    score_percent INT NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mini_task_attempts_task ON mini_task_attempts (mini_task_id);
CREATE INDEX idx_mini_task_attempts_enrollment ON mini_task_attempts (enrollment_id);
