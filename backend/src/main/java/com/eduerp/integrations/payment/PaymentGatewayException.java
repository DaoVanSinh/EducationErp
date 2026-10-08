package com.eduerp.integrations.payment;

/**
 * Cổng thanh toán từ chối yêu cầu hoặc trả về phản hồi không dùng được (resultCode khác 0, thiếu
 * payUrl, không có phản hồi...). KHÔNG phải {@code AppException} - lý do giống
 * {@link PaymentSignatureException}: {@code integrations.payment} không được phụ thuộc
 * {@code modules.billing}. Usecase gọi client (Task 14's fix pass) bắt lỗi này và dịch sang một
 * {@code BillingException} phù hợp.
 */
public class PaymentGatewayException extends RuntimeException {

    private final PaymentGatewayType gateway;

    public PaymentGatewayException(PaymentGatewayType gateway, String message) {
        super(message);
        this.gateway = gateway;
    }

    public PaymentGatewayType gateway() {
        return gateway;
    }
}
