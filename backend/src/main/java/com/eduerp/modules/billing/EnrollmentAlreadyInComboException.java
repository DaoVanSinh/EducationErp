package com.eduerp.modules.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Review Focus #3: hai request đồng thời cùng chọn một ghi danh vào hai combo khác nhau. UNIQUE
 * {@code combo_enrollments.enrollment_id} chặn một trong hai ở tầng DB; lỗi này là bản dịch nghiệp
 * vụ của nó. Nhận cả danh sách vì tại thời điểm bắt {@code DataIntegrityViolationException} không
 * biết được id nào đã thua - nói "một trong các ghi danh" là sự thật, chỉ đích danh một cái là đoán.
 */
public final class EnrollmentAlreadyInComboException extends BillingException {
    public EnrollmentAlreadyInComboException(List<UUID> enrollmentIds) {
        super("BILLING_ENROLLMENT_ALREADY_IN_COMBO", HttpStatus.CONFLICT,
                "Một trong các ghi danh " + enrollmentIds + " đã nằm trong một combo khác");
    }
}
