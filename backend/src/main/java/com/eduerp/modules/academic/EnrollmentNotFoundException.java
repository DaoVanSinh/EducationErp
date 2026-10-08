package com.eduerp.modules.academic;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class EnrollmentNotFoundException extends AcademicException {

    public EnrollmentNotFoundException(UUID enrollmentId) {
        super("ACADEMIC_ENROLLMENT_NOT_FOUND", HttpStatus.BAD_REQUEST,
                "Không tìm thấy lượt ghi danh " + enrollmentId);
    }
}
