package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * {@code minCourseCount} tối thiểu là 2 vì combo tối thiểu 2 khoá (spec mục 6 bước 1) - một bậc cho
 * mốc 1 khoá sẽ không bao giờ được dùng tới. Kiểu bao {@code Integer} để {@code @NotNull} bắt được
 * trường thiếu thay vì mặc định 0.
 */
public record CreateComboDiscountTierRequest(@NotNull @Min(2) Integer minCourseCount,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent) {
}
