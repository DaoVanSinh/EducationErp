package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking.
 * {@code courseCount} là số khoá trong combo, trả sẵn để danh sách không phải tải cả danh sách con
 * chỉ để đếm.
 */
public record ComboResponse(UUID id, UUID studentProfileId, UUID branchId, BigDecimal totalOriginalAmount,
        BigDecimal discountPercent, BigDecimal totalDiscountedAmount, LocalDate dueDate, Instant createdAt,
        int courseCount) {
}
