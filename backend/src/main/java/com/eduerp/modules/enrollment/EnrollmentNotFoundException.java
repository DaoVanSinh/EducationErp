package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class EnrollmentNotFoundException extends EnrollmentException {
    public EnrollmentNotFoundException(UUID enrollmentId) {
        super("ENROLLMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy ghi danh " + enrollmentId);
    }
}
