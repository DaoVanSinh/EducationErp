CREATE TABLE combo_discount_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- "từ N khoá trở lên". UNIQUE vì nếu tồn tại hai bậc cùng mốc thì việc chọn bậc sẽ phụ thuộc
    -- thứ tự dòng trong bảng thay vì một quy tắc xác định (spec mục 4).
    min_course_count INT NOT NULL UNIQUE,
    discount_percent NUMERIC(5,2) NOT NULL,
    -- Hệ thống này không có Actions.DELETE ở bất kỳ resource nào: "xoá" một bậc là tắt active,
    -- mirror courses.active.
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE combos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- student_profile_id/branch_id là snapshot chốt lúc tạo combo (spec mục 4): FK mức DB để toàn
    -- vẹn dữ liệu, không có @ManyToOne trong Java (rule #3 - module khác).
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    total_original_amount NUMERIC(14,0) NOT NULL,
    -- Snapshot: bậc giảm giá đổi về sau không được làm đổi combo đã tạo.
    discount_percent NUMERIC(5,2) NOT NULL,
    total_discounted_amount NUMERIC(14,0) NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id)
);

CREATE INDEX idx_combos_student ON combos (student_profile_id);

-- Không có cột status: huỷ combo là xoá cứng (chỉ được phép khi chưa có hoá đơn nào), nên không
-- tồn tại trạng thái CANCELLED song song như invoices (spec mục 4).
CREATE TABLE combo_enrollments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    combo_id UUID NOT NULL REFERENCES combos(id) ON DELETE CASCADE,
    -- UNIQUE ĐẦY ĐỦ, không PARTIAL như V17/V20: huỷ combo xoá cứng dòng này, nên dòng chỉ tồn tại
    -- khi combo đó còn sống - một ghi danh chỉ nằm trong đúng một combo tại một thời điểm. Đây là
    -- lớp chặn cuối cho race hai request cùng gộp một enrollment vào hai combo (spec mục 4).
    enrollment_id UUID NOT NULL UNIQUE REFERENCES enrollments(id),
    course_id UUID NOT NULL REFERENCES courses(id),
    original_tuition_fee NUMERIC(14,0) NOT NULL
);

CREATE INDEX idx_combo_enrollments_combo ON combo_enrollments (combo_id);
