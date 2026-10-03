package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class DuplicateActiveEnrollmentException extends EnrollmentException {
    public DuplicateActiveEnrollmentException(UUID studentProfileId, UUID classId) {
        super("ENROLLMENT_DUPLICATE_ACTIVE", HttpStatus.CONFLICT,
                "Học viên " + studentProfileId + " đã ghi danh lớp " + classId + " và còn đang học");
    }
}
