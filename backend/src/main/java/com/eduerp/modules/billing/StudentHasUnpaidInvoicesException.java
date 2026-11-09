package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class StudentHasUnpaidInvoicesException extends BillingException {
    public StudentHasUnpaidInvoicesException(UUID accountId) {
        super("BILLING_STUDENT_HAS_UNPAID_INVOICES", HttpStatus.CONFLICT,
                "Học viên còn nợ học phí, hãy thu xong hoặc huỷ hoá đơn trước khi đóng tài khoản");
    }
}
