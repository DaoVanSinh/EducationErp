package com.eduerp.integrations.payment;

/**
 * Chữ ký HMAC của callback không khớp. KHÔNG phải {@code AppException}: {@code integrations.payment}
 * không được phụ thuộc {@code modules.billing} nên không thể ném
 * {@code BillingException.InvalidCallbackSignatureException} tại đây.
 * {@code modules.billing.usecase.HandlePaymentCallback} bắt lỗi này và ném lại
 * {@code InvalidCallbackSignatureException} như spec mục 5 bước 1 mô tả.
 *
 * <p>Message cố tình KHÔNG chứa orderId hay bất kỳ tham số nào của request - không để chuỗi này rơi
 * vào log/response rồi thành oracle (Review Focus #5).
 */
public class PaymentSignatureException extends RuntimeException {

    private final PaymentGatewayType gateway;

    public PaymentSignatureException(PaymentGatewayType gateway) {
        super("Chữ ký callback của cổng " + gateway + " không hợp lệ");
        this.gateway = gateway;
    }

    public PaymentGatewayType gateway() {
        return gateway;
    }
}
