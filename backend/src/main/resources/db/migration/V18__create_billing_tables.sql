CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL REFERENCES enrollments(id),
    -- student_profile_id/course_id/branch_id là snapshot chốt lúc phát hành (spec mục 5): FK mức DB
    -- để toàn vẹn dữ liệu, không có @ManyToOne trong Java (rule #3 - module khác).
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id),
    course_id UUID NOT NULL REFERENCES courses(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    installment_number INT NOT NULL,
    amount NUMERIC(14,0) NOT NULL,
    amount_paid NUMERIC(14,0) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
    due_date DATE NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id)
);

CREATE INDEX idx_invoices_enrollment ON invoices (enrollment_id);
CREATE INDEX idx_invoices_student ON invoices (student_profile_id);
-- Job quét quá hạn (MarkOverdueInvoices) lọc đúng theo (status, due_date); không có index này thì
-- mỗi 1h sáng là một lần quét toàn bảng hoá đơn.
CREATE INDEX idx_invoices_status_due_date ON invoices (status, due_date);

-- Giới hạn 3 đợt/ghi danh được enforce ở usecase (CreateInvoice, Task 13) thay vì CHECK ở đây: số
-- đợt là quyết định nghiệp vụ có thể đổi, và invoice CANCELLED không được tính vào hạn mức - logic
-- đó không diễn đạt được bằng một CHECK trên một dòng.

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    amount NUMERIC(14,0) NOT NULL,
    method VARCHAR(16) NOT NULL,
    -- UNIQUE trên cột nullable: Postgres không coi hai NULL là trùng nhau, nên nhiều bản ghi MANUAL
    -- (không có mã cổng) cùng tồn tại, còn mỗi orderId của cổng chỉ sinh được một bản ghi duy nhất -
    -- lớp phòng thủ DB cho Review Focus #4 (callback gọi 2 lần).
    gateway_transaction_id VARCHAR(128) UNIQUE,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id);
