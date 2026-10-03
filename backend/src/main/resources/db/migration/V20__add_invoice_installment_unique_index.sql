-- Final review Important #3: lớp phòng thủ DB cho bất biến "tối đa 3 đợt, tổng không vượt học phí"
-- (spec mục 5). CreateInvoice đếm rồi mới ghi, không khoá gì - hai request đồng thời có thể cùng đọc
-- count=1 và cùng tính installmentNumber=2. Index PARTIAL (loại CANCELLED) để một đợt bị huỷ không
-- chiếm vĩnh viễn số thứ tự của nó - mirror đúng cách uq_enrollments_active_student_class (V17) đã
-- làm cho ghi danh trùng (Review Focus #1).
CREATE UNIQUE INDEX uq_invoices_enrollment_installment
    ON invoices (enrollment_id, installment_number)
    WHERE status <> 'CANCELLED';
