package com.eduerp.modules.courses.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GradeColumnResponse(UUID id, String name, BigDecimal weightPercent, BigDecimal maxScore,
        int displayOrder) {
}
