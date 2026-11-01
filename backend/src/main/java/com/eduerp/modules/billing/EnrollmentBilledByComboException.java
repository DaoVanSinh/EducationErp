package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Ghi danh trong combo được thu qua hoá đơn của combo; phát hành thêm hoá đơn lẻ là thu tiền hai lần. */
public final class EnrollmentBilledByComboException extends BillingException {
    public EnrollmentBilledByComboException(UUID enrollmentId) {
        super("BILLING_ENROLLMENT_BILLED_BY_COMBO", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " nằm trong một combo, hãy phát hành hoá đơn ở combo đó");
    }
}
