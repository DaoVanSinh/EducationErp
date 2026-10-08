package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Dùng cho endpoint tra trạng thái thanh toán theo {@code gatewayTransactionId}. KHÔNG dùng trong
 * {@code HandlePaymentCallback}: ở đó orderId lạ phải đi vào nhánh "bỏ qua im lặng" (Review Focus #5). */
public final class PaymentNotFoundException extends BillingException {
    public PaymentNotFoundException(String gatewayTransactionId) {
        super("BILLING_PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Không tìm thấy giao dịch " + gatewayTransactionId);
    }
}
