package com.eduerp.integrations.payment;

/** {@code gatewayOrderId} là mã mà cổng dùng để gọi IPN về - billing lưu nó vào
 * {@code Payment.gatewayTransactionId} để tra cứu lúc nhận callback. */
public record PaymentUrlResult(String payUrl, String gatewayOrderId) {
}
