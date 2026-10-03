package com.eduerp.integrations.payment;

import java.math.BigDecimal;

/** Kết quả sau khi chữ ký đã được xác thực. {@code success=false} = cổng báo giao dịch thất bại
 * (khác hoàn toàn với chữ ký sai - trường hợp đó ném {@link PaymentSignatureException}). */
public record PaymentCallbackResult(String orderId, boolean success, BigDecimal amount, String rawMessage) {
}
