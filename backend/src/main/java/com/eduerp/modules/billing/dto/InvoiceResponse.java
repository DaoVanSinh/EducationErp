package com.eduerp.modules.billing.dto;

import com.eduerp.modules.billing.BillingConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking. */
public record InvoiceResponse(UUID id, UUID enrollmentId, UUID studentProfileId, UUID courseId, UUID branchId,
        int installmentNumber, BigDecimal amount, BigDecimal amountPaid, BillingConstants.InvoiceStatus status,
        LocalDate dueDate, Instant issuedAt) {
}
