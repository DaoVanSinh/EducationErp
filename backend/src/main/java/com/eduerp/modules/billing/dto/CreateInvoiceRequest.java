package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Kế toán tự nhập số tiền mỗi đợt, hệ thống KHÔNG tự chia đều (spec mục 12). */
public record CreateInvoiceRequest(@NotNull UUID enrollmentId, @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate) {
}
