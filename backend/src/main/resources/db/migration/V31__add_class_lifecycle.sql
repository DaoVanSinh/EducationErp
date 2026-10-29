-- Vòng đời lớp học: Đang tuyển sinh → Đang học → Đã kết thúc, hoặc Đang tuyển sinh → Đã huỷ.
-- Thay cờ active/inactive đơn giản của Phân hệ 2 (spec 2026-10-02 mục 1 "ngoài phạm vi" - nay bổ sung):
-- trung tâm cần biết lớp khai giảng ngày nào, cần tối thiểu bao nhiêu học viên mới mở, và lúc nào lớp
-- thật sự bắt đầu/kết thúc để điểm danh, tiến trình, ghi danh theo đúng mốc đó.
ALTER TABLE classes
    ADD COLUMN status     VARCHAR(16),
    ADD COLUMN start_date DATE,
    ADD COLUMN end_date   DATE,
    ADD COLUMN min_seats  INT,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN ended_at   TIMESTAMPTZ;

-- Dữ liệu cũ, theo thứ tự ưu tiên:
-- 1. Lớp đã vô hiệu hoá → Đã kết thúc (giữ nguyên điểm/điểm danh để còn tra cứu).
UPDATE classes c
SET status     = 'COMPLETED',
    start_date = COALESCE((SELECT MIN((e.enrolled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
                           FROM enrollments e WHERE e.class_id = c.id), CURRENT_DATE),
    started_at = (SELECT MIN(e.enrolled_at) FROM enrollments e WHERE e.class_id = c.id),
    end_date   = CURRENT_DATE,
    ended_at   = now()
WHERE c.active = false;

-- 2. Lớp đang hoạt động đã có người ghi danh → Đang học, khai giảng = ngày ghi danh sớm nhất.
UPDATE classes c
SET status     = 'IN_PROGRESS',
    start_date = (SELECT MIN((e.enrolled_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date)
                  FROM enrollments e WHERE e.class_id = c.id),
    started_at = (SELECT MIN(e.enrolled_at) FROM enrollments e WHERE e.class_id = c.id)
WHERE c.status IS NULL
  AND EXISTS (SELECT 1 FROM enrollments e WHERE e.class_id = c.id);

-- 3. Còn lại (đang hoạt động, chưa ai ghi danh) → Đang tuyển sinh, khai giảng tạm = hôm nay.
UPDATE classes
SET status     = 'ENROLLING',
    start_date = CURRENT_DATE
WHERE status IS NULL;

UPDATE classes SET min_seats = 1;

ALTER TABLE classes
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN min_seats SET NOT NULL,
    ADD CONSTRAINT ck_classes_status CHECK (status IN ('ENROLLING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_classes_min_seats CHECK (min_seats >= 1 AND min_seats <= max_seats),
    ADD CONSTRAINT ck_classes_end_date CHECK (end_date IS NULL OR end_date >= start_date);

ALTER TABLE classes DROP COLUMN active;

-- Tác vụ tự khai giảng mỗi ngày quét theo trạng thái + ngày khai giảng.
CREATE INDEX idx_classes_status_start ON classes (status, start_date);
