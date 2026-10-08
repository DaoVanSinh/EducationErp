package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/** Không có {@code code}: mã khóa học bất biến sau khi tạo. */
public record UpdateCourseRequest(@NotBlank String name, String description,
        Integer standardSessionCount, @PositiveOrZero BigDecimal tuitionFee, boolean active) {
}
