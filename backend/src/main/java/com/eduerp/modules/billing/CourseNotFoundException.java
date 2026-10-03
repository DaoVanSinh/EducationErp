package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class CourseNotFoundException extends BillingException {
    public CourseNotFoundException(UUID courseId) {
        super("BILLING_COURSE_NOT_FOUND", HttpStatus.BAD_REQUEST, "Không tìm thấy khoá học " + courseId);
    }
}
