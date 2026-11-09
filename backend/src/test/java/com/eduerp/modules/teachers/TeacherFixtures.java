package com.eduerp.modules.teachers;

import com.eduerp.modules.teachers.internal.model.TeacherProfile;
import com.eduerp.modules.teachers.internal.repository.TeacherProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Giáo viên phân công được: có hồ sơ và đã thiết lập lương. Chi nhánh chủ quản nằm trên tài khoản, do test tự chọn. */
public final class TeacherFixtures {

    private TeacherFixtures() {
    }

    /** Ghi lương bằng SQL: nạp hồ sơ rồi gọi applyPayTerms ngoài transaction sẽ lỗi lazy ở danh sách phụ cấp. */
    public static void makeAssignable(TeacherProfileRepository profiles, JdbcTemplate jdbc, UUID accountId) {
        if (!profiles.existsByAccountId(accountId)) {
            profiles.save(new TeacherProfile(accountId, List.of("Toán"), null, null));
        }
        jdbc.update("""
                UPDATE teacher_profiles
                SET pay_type = 'HOURLY', hourly_rate = 150000, work_start_date = CURRENT_DATE
                WHERE account_id = ?
                """, accountId);
    }
}
