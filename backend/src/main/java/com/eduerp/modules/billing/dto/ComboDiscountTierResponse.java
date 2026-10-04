package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking. */
public record ComboDiscountTierResponse(UUID id, int minCourseCount, BigDecimal discountPercent,
        boolean active) {
}
