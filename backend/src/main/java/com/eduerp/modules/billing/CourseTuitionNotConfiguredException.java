package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class CourseTuitionNotConfiguredException extends BillingException {
    public CourseTuitionNotConfiguredException(UUID courseId) {
        super("BILLING_COURSE_TUITION_NOT_CONFIGURED", HttpStatus.CONFLICT,
                "Khoá học " + courseId + " chưa gắn học phí");
    }
}
