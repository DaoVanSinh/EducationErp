package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassFullException extends EnrollmentException {
    public ClassFullException(UUID classId, int maxSeats) {
        super("ENROLLMENT_CLASS_FULL", HttpStatus.CONFLICT,
                "Lớp học " + classId + " đã đủ " + maxSeats + " chỗ");
    }
}
