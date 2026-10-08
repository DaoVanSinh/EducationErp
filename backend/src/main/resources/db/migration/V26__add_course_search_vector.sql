-- Retrieval của Phân hệ 5 (spec Ruling R1): full-text search của chính Postgres, KHÔNG vector DB.
--
-- Cấu hình 'simple' chứ không 'english': Postgres không có cấu hình tiếng Việt dựng sẵn, và
-- 'english' sẽ áp luật stemming tiếng Anh lên từ tiếng Việt ("tiếng" -> "tieng"? "giao" -> "giao"?)
-- làm lệch kết quả. 'simple' chỉ tách từ + viết thường, đúng việc cần làm.
--
-- GENERATED ALWAYS ... STORED: cột tự đồng bộ mỗi khi name/description đổi, không code Java nào
-- phải nhớ cập nhật nó - và vì vậy KHÔNG có field nào tương ứng trên entity Course.
-- Bắt buộc dùng dạng 2 tham số to_tsvector('simple', ...): chỉ dạng đó là IMMUTABLE, dạng 1 tham số
-- là STABLE và Postgres sẽ từ chối nó trong một generated column.
ALTER TABLE courses ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(description, ''))
    ) STORED;

-- GIN là index đúng cho tsvector: một danh mục vài chục khóa chưa cần nó về hiệu năng, nhưng toán tử
-- @@ không dùng được index B-tree nên không có GIN thì câu truy vấn là seq scan mãi mãi.
CREATE INDEX idx_courses_search_vector ON courses USING GIN (search_vector);
