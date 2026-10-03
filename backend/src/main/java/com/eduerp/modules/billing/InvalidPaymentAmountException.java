package com.eduerp.modules.billing;

import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public final class InvalidPaymentAmountException extends BillingException {
    public InvalidPaymentAmountException(BigDecimal amount) {
        super("BILLING_INVALID_PAYMENT_AMOUNT", HttpStatus.BAD_REQUEST,
                "Số tiền " + amount.toPlainString() + " không hợp lệ cho hoá đơn này");
    }
}
