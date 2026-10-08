package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * KHÔNG chứa {@code gatewayTransactionId} và không chứa dữ liệu cá nhân nào - endpoint tra trạng
 * thái là public (Task 21/22), nên payload phải an toàn khi ai cũng gọi được bằng một
 * {@code gatewayTransactionId} hợp lệ.
 */
public record PaymentResponse(UUID id, BigDecimal amount, BillingConstants.PaymentMethod method,
        BillingConstants.PaymentStatus status, Instant paidAt, Instant createdAt) {
}
