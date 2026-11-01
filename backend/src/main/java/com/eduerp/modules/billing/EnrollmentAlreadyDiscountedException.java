package com.eduerp.modules.billing;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Combo và đăng ký nhóm loại trừ nhau: một ghi danh đã hưởng một ưu đãi thì không được gộp vào ưu đãi thứ
 * hai. Nhận cả danh sách vì lỗi có thể đến từ ràng buộc UNIQUE ở DB, lúc đó không biết đích danh id nào.
 */
public final class EnrollmentAlreadyDiscountedException extends BillingException {
    public EnrollmentAlreadyDiscountedException(List<UUID> enrollmentIds) {
        super("BILLING_ENROLLMENT_ALREADY_DISCOUNTED", HttpStatus.CONFLICT,
                "Một trong các ghi danh " + enrollmentIds + " đã nằm trong một combo hoặc một nhóm khác");
    }
}
