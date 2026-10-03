package com.eduerp.modules.billing;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class InvoiceAmountExceedsTuitionException extends BillingException {
    public InvoiceAmountExceedsTuitionException(BigDecimal totalAfterThisInvoice, BigDecimal tuitionFee) {
        super("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION", HttpStatus.CONFLICT,
                "Tổng các đợt thu " + totalAfterThisInvoice.toPlainString() + " vượt học phí khoá học "
                        + tuitionFee.toPlainString());
    }
}
