package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record CreateCourseRequest(@NotBlank String code, @NotBlank String name, String description,
        Integer standardSessionCount, @PositiveOrZero BigDecimal tuitionFee) {
}
