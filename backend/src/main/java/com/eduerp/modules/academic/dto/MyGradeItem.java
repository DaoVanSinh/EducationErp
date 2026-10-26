package com.eduerp.modules.academic.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một cột điểm trong cổng học tập của học viên. {@code score} null = giáo viên chưa chấm cột này,
 * khác với điểm 0.
 */
public record MyGradeItem(UUID columnId, String name, BigDecimal weightPercent, BigDecimal maxScore,
        BigDecimal score) {
}
