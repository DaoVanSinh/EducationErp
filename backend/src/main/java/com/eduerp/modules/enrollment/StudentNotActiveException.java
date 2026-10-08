package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class StudentNotActiveException extends EnrollmentException {
    public StudentNotActiveException(UUID studentProfileId) {
        super("ENROLLMENT_STUDENT_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Hồ sơ học viên " + studentProfileId + " đã bị vô hiệu hoá");
    }
}
