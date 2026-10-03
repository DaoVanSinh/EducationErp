package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/**
 * Cổng thanh toán từ chối tạo link hoặc không trả phản hồi dùng được (final review Critical #1).
 * 502 vì lỗi nằm ở phía đối tác bên ngoài, không phải yêu cầu của người dùng.
 */
public final class PaymentGatewayUnavailableException extends BillingException {
    public PaymentGatewayUnavailableException(String message) {
        super("BILLING_PAYMENT_GATEWAY_UNAVAILABLE", HttpStatus.BAD_GATEWAY, message);
    }
}
