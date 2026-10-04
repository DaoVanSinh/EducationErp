package com.eduerp.modules.billing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Không có {@code minCourseCount}: đổi mốc của một bậc đã dùng là tạo một bậc khác. Không có thao
 * tác xoá - đặt {@code active = false} là cách "xoá" một bậc (spec mục 4, mirror {@code Course.active}).
 */
public record UpdateComboDiscountTierRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPercent, boolean active) {
}
