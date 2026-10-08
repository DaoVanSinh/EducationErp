package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record CreateCourseRequest(@NotBlank String code, @NotBlank String name, String description,
        Integer standardSessionCount, @PositiveOrZero BigDecimal tuitionFee,
        @NotNull @Valid List<GradeColumnSlot> gradeColumns) {
}
