-- Một Invoice thuộc về ĐÚNG MỘT trong hai: một Enrollment (luồng Phase 3, giữ nguyên 100%) hoặc
-- một Combo (luồng mới) - spec mục 5. Mọi Invoice hiện có đều có enrollment_id + course_id và
-- combo_id null, nên thoả CHECK ngay: KHÔNG cần backfill.
ALTER TABLE invoices ALTER COLUMN enrollment_id DROP NOT NULL;
ALTER TABLE invoices ALTER COLUMN course_id DROP NOT NULL;
ALTER TABLE invoices ADD COLUMN combo_id UUID REFERENCES combos(id);
ALTER TABLE invoices ADD CONSTRAINT chk_invoices_enrollment_xor_combo
    CHECK ((enrollment_id IS NOT NULL AND combo_id IS NULL)
        OR (enrollment_id IS NULL AND combo_id IS NOT NULL));

-- GetComboDetail/CancelCombo lọc đúng theo combo_id; không có index này thì mỗi lần mở chi tiết
-- combo là một lần quét toàn bảng hoá đơn.
CREATE INDEX idx_invoices_combo ON invoices (combo_id);

-- Mirror V20: tối đa 3 đợt theo combo, invoice CANCELLED không chiếm chỗ. Vế
-- "combo_id IS NOT NULL" là thừa về mặt logic (Postgres coi hai NULL là khác nhau trong unique
-- index) nhưng giữ lại để index chỉ chứa dòng combo, không phình theo mọi hoá đơn đơn-khoá.
CREATE UNIQUE INDEX uq_invoices_combo_installment
    ON invoices (combo_id, installment_number)
    WHERE status <> 'CANCELLED' AND combo_id IS NOT NULL;

-- uq_invoices_enrollment_installment (V20) không cần sửa: enrollment_id của hoá đơn combo là NULL,
-- mà Postgres không coi hai NULL là trùng nhau, nên index cũ tự động bỏ qua các dòng combo.
