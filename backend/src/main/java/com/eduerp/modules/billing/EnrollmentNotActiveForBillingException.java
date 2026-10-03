package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Rút khỏi lớp giữ nguyên công nợ đã phát sinh, chỉ chặn phát hành đợt MỚI (spec mục 12). */
public final class EnrollmentNotActiveForBillingException extends BillingException {
    public EnrollmentNotActiveForBillingException(UUID enrollmentId) {
        super("BILLING_ENROLLMENT_NOT_ACTIVE", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " không còn đang học, không phát hành thêm đợt thu");
    }
}
