package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Kế toán ghi nhận một lần thu tay. {@code method} bỏ trống = tiền mặt (client cũ chỉ gửi
 * {@code amount} vẫn chạy). {@code note}: số phiếu thu / nội dung chuyển khoản để đối soát.
 */
public record RecordManualPaymentRequest(
        @NotNull @Positive BigDecimal amount,
        BillingConstants.OfflinePaymentMethod method,
        @Size(max = BillingConstants.Limits.MAX_PAYMENT_NOTE_LENGTH) String note) {

    public RecordManualPaymentRequest {
        if (method == null) {
            method = BillingConstants.OfflinePaymentMethod.CASH;
        }
    }

    public RecordManualPaymentRequest(BigDecimal amount) {
        this(amount, BillingConstants.OfflinePaymentMethod.CASH, null);
    }
}
