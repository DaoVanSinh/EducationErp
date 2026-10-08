package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Một dòng trong cấu hình cột điểm của khóa học khi tạo/sửa - mirror {@link WeeklyScheduleSlot}:
 * không có {@code id} vì PATCH thay TOÀN BỘ danh sách, không vá từng dòng (spec mục 4).
 * {@code maxScore} phải dương vì nó là số bị chia trong công thức điểm tổng kết (spec mục 6).
 */
public record GradeColumnSlot(@NotBlank String name, @NotNull @Positive BigDecimal weightPercent,
        @NotNull @Positive BigDecimal maxScore, @PositiveOrZero int displayOrder) {
}
