package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * KHÔNG chứa {@code gatewayTransactionId} và không chứa dữ liệu cá nhân nào - endpoint tra trạng
 * thái là public (Task 21/22), nên payload phải an toàn khi ai cũng gọi được bằng một
 * {@code gatewayTransactionId} hợp lệ.
 *
 * <p>{@code note} (V35) chỉ có ở lần thu tay - ghi chú kế toán nhập. Endpoint public tra theo
 * {@code gatewayTransactionId} chỉ trả giao dịch qua cổng, mà giao dịch qua cổng luôn có
 * {@code note = null}, nên ghi chú không lộ ra đường không cần đăng nhập.
 */
public record PaymentResponse(UUID id, BigDecimal amount, BillingConstants.PaymentMethod method,
        BillingConstants.PaymentStatus status, Instant paidAt, Instant createdAt, String note) {
}
