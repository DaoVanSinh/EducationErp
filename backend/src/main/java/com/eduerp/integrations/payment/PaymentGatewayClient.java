package com.eduerp.integrations.payment;

import java.util.Map;

/**
 * Một cổng thanh toán. Mirror {@code StorageClient} ở mức trừu tượng: cơ chế thuần, không biết
 * nghiệp vụ. {@code modules.billing.internal.PaymentGatewayClientResolver} nhận
 * {@code List<PaymentGatewayClient>} từ Spring rồi map theo {@link #type()}.
 */
public interface PaymentGatewayClient {

    PaymentGatewayType type();

    PaymentUrlResult createPaymentUrl(PaymentRequest request);

    /**
     * Xác thực chữ ký rồi dịch tham số thô của cổng sang {@link PaymentCallbackResult}.
     *
     * @throws PaymentSignatureException khi chữ ký không khớp - nơi gọi phải coi request đó là giả.
     */
    PaymentCallbackResult verifyCallback(Map<String, String> rawParams);
}
