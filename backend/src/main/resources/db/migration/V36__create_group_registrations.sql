-- Đăng ký nhóm: nhiều học viên rủ nhau học CÙNG một khoá, mỗi người được giảm theo số người trong nhóm.
-- Khác combo (V21): combo là MỘT học viên nhiều khoá, thu như một đơn vị; nhóm là NHIỀU học viên, mỗi
-- người vẫn tự đóng học phí của mình qua hoá đơn theo ghi danh, chỉ là trần học phí đã được giảm.
CREATE TABLE group_registrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL REFERENCES courses(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    -- Snapshot lúc tạo: đổi bậc giảm giá về sau không làm đổi một nhóm đã chốt.
    discount_percent NUMERIC(5,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    created_by_account_id UUID NOT NULL REFERENCES accounts(id)
);

CREATE INDEX idx_group_registrations_branch ON group_registrations (branch_id);
CREATE INDEX idx_group_registrations_course ON group_registrations (course_id);

-- Huỷ nhóm là xoá cứng (chỉ khi chưa thành viên nào có hoá đơn sống), nên UNIQUE enrollment_id là unique
-- đầy đủ: một ghi danh chỉ thuộc đúng một nhóm tại một thời điểm - mirror combo_enrollments.
CREATE TABLE group_registration_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES group_registrations(id) ON DELETE CASCADE,
    enrollment_id UUID NOT NULL UNIQUE REFERENCES enrollments(id),
    student_profile_id UUID NOT NULL REFERENCES student_profiles(id),
    original_tuition_fee NUMERIC(14,0) NOT NULL,
    discounted_tuition_fee NUMERIC(14,0) NOT NULL,
    -- Một người không được đếm hai lần để đẩy nhóm lên bậc giảm cao hơn.
    CONSTRAINT uq_group_member_student UNIQUE (group_id, student_profile_id)
);

CREATE INDEX idx_group_registration_members_group ON group_registration_members (group_id);
