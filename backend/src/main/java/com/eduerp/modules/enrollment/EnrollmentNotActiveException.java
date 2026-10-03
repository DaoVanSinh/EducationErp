package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class EnrollmentNotActiveException extends EnrollmentException {
    public EnrollmentNotActiveException(UUID enrollmentId) {
        super("ENROLLMENT_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " không còn ở trạng thái đang học");
    }
}
