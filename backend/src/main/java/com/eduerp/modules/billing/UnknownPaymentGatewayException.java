package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Ném khi ai đó gọi thu online với {@code MANUAL} (hoặc một method tương lai chưa có client). */
public final class UnknownPaymentGatewayException extends BillingException {
    public UnknownPaymentGatewayException(BillingConstants.PaymentMethod method) {
        super("BILLING_UNKNOWN_PAYMENT_GATEWAY", HttpStatus.BAD_REQUEST,
                "Phương thức " + method + " không phải một cổng thanh toán online");
    }
}
