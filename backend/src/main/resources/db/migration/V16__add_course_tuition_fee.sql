-- Học phí tính theo Course, không theo Class (spec mục 12). NUMERIC(14,0): VND không có phần thập
-- phân, scale 0 để con số hiển thị khớp đúng con số lưu xuống.
-- Nullable: khoá học đã tồn tại chưa gắn giá, và khoá học mới có thể tạo trước khi chốt giá.
ALTER TABLE courses ADD COLUMN tuition_fee NUMERIC(14,0);
