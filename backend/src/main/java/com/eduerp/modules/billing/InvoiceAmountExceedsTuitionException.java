package com.eduerp.modules.billing;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class InvoiceAmountExceedsTuitionException extends BillingException {
    public InvoiceAmountExceedsTuitionException(BigDecimal totalAfterThisInvoice, BigDecimal tuitionFee) {
        super("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION", HttpStatus.CONFLICT,
                "Tổng các đợt thu " + totalAfterThisInvoice.toPlainString() + " vượt học phí khoá học "
                        + tuitionFee.toPlainString());
    }

    private InvoiceAmountExceedsTuitionException(String message) {
        super("BILLING_INVOICE_AMOUNT_EXCEEDS_TUITION", HttpStatus.CONFLICT, message);
    }

    /** Spec mục 6: tên và errorCode giữ nguyên dù ngữ cảnh là combo, vì bản chất lỗi giống nhau -
     * "vượt tổng tiền phải thu". Chỉ mốc so sánh đổi: tổng combo SAU giảm giá. */
    public static InvoiceAmountExceedsTuitionException forCombo(BigDecimal totalAfterThisInvoice,
            BigDecimal totalDiscountedAmount) {
        return new InvoiceAmountExceedsTuitionException("Tổng các đợt thu "
                + totalAfterThisInvoice.toPlainString() + " vượt tổng tiền combo sau giảm giá "
                + totalDiscountedAmount.toPlainString());
    }
}
