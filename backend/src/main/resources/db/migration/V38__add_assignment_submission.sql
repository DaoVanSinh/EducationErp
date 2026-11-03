-- Học viên nộp bài tập / mini-test ngay trên hệ thống (V29 chỉ theo dõi bài làm ngoài hệ thống).
-- Cả hai cột nullable: giáo viên vẫn được ghi nhận tay bài nộp giấy, khi đó không có nội dung.
ALTER TABLE assignment_results
    ADD COLUMN submission_content VARCHAR(4000),
    ADD COLUMN submitted_at TIMESTAMPTZ;
