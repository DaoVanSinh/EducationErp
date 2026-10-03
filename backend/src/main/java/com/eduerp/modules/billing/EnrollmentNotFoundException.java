package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Cùng tên đơn giản với {@code modules.enrollment.EnrollmentNotFoundException} nhưng khác package và
 * khác errorCode - đây là lỗi của billing khi id ghi danh trong body không trỏ tới bản ghi nào. */
public final class EnrollmentNotFoundException extends BillingException {
    public EnrollmentNotFoundException(UUID enrollmentId) {
        super("BILLING_ENROLLMENT_NOT_FOUND", HttpStatus.BAD_REQUEST, "Không tìm thấy ghi danh " + enrollmentId);
    }
}
