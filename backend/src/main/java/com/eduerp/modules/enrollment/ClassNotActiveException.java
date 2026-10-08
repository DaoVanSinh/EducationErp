package com.eduerp.modules.enrollment;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ClassNotActiveException extends EnrollmentException {
    public ClassNotActiveException(UUID classId) {
        super("ENROLLMENT_CLASS_NOT_ACTIVE", HttpStatus.CONFLICT, "Lớp học " + classId + " đã bị vô hiệu hoá");
    }
}
