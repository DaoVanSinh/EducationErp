-- Mini-task theo cách các nền tảng luyện tập phổ biến (Khan Academy, Quizizz, Moodle) đang làm:
-- 1) Ngưỡng "Đạt" (mastery): học viên làm lại tới khi điểm cao nhất >= pass_percent. Mặc định 80%,
--    khớp AssessmentConstants.Limits.DEFAULT_PASS_PERCENT. Mini-task cũ nhận mặc định này.
-- 2) Giải thích đáp án cho từng câu, chỉ hiện SAU khi nộp bài - ôn luyện cần biết vì sao sai.

ALTER TABLE mini_tasks
    ADD COLUMN pass_percent INT NOT NULL DEFAULT 80,
    ADD CONSTRAINT ck_mini_tasks_pass_percent CHECK (pass_percent BETWEEN 1 AND 100);

ALTER TABLE mini_task_questions
    ADD COLUMN explanation VARCHAR(1000);
