package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class InstallmentLimitExceededException extends BillingException {
    public InstallmentLimitExceededException(UUID enrollmentId, int maxInstallments) {
        super("BILLING_INSTALLMENT_LIMIT_EXCEEDED", HttpStatus.CONFLICT,
                "Ghi danh " + enrollmentId + " đã có đủ " + maxInstallments + " đợt thu");
    }
}
