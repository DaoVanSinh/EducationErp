package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Mirror {@code CreateInvoiceRequest} nhưng neo theo {@code comboId} thay vì {@code enrollmentId}:
 * id của đơn vị thu nằm trong body, đúng như {@code POST /api/billing/invoices} đã làm (spec mục 6).
 * Kế toán tự nhập số tiền mỗi đợt, hệ thống KHÔNG tự chia đều.
 */
public record CreateComboInvoiceRequest(@NotNull UUID comboId, @NotNull @Positive BigDecimal amount,
        @NotNull LocalDate dueDate) {
}
