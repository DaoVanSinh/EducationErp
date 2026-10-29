package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Đổi vai trò làm mất năng lực học viên khi người đó còn đang học ít nhất một lớp. */
public final class StudentHasActiveEnrollmentsException extends EnrollmentException {
    public StudentHasActiveEnrollmentsException(UUID accountId) {
        super("ENROLLMENT_STUDENT_HAS_ACTIVE_ENROLLMENTS", HttpStatus.CONFLICT,
                "Tài khoản " + accountId + " còn đang học - hãy rút hoặc hoàn tất các ghi danh trước khi đổi vai trò");
    }
}
