package com.eduerp.modules.academic.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code classId} có trong body vì mọi kiểm tra quyền của module này đều theo LỚP (spec mục 6), và
 * {@code EnrollmentManagement.EnrollmentSummaryResponse} không mang {@code classId} - xem javadoc
 * {@code RecordGrade}. Trần của {@code score} là {@code maxScore} của riêng cột nên không đặt được
 * bằng annotation ở đây, chặn trong usecase (Review Focus #10).
 */
public record RecordGradeRequest(@NotNull UUID classId, @NotNull UUID enrollmentId,
        @NotNull UUID courseGradeColumnId, @NotNull @PositiveOrZero BigDecimal score) {
}
