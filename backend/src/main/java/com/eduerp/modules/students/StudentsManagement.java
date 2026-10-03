package com.eduerp.modules.students;

import com.eduerp.modules.students.internal.repository.StudentProfileRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade của module students. {@code modules.enrollment} gọi {@link #getProfile(UUID)} để xác nhận
 * học viên tồn tại và còn hoạt động trước khi ghi danh (spec mục 4).
 */
@Service
public class StudentsManagement {

    /** {@code studentProfileId} là khoá của hồ sơ học viên, KHÔNG phải accountId - ghi danh tham
     * chiếu hồ sơ nghiệp vụ, không tham chiếu tài khoản đăng nhập. */
    public record StudentSummaryResponse(UUID studentProfileId, UUID accountId, boolean active) {
    }

    private final StudentProfileRepository profiles;

    StudentsManagement(StudentProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public boolean studentProfileExists(UUID accountId) {
        return profiles.existsByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public Optional<StudentSummaryResponse> getProfile(UUID studentProfileId) {
        return profiles.findById(studentProfileId).map(profile ->
                new StudentSummaryResponse(profile.getId(), profile.getAccountId(), profile.isActive()));
    }
}
