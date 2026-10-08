package com.eduerp.modules.courses.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CourseResponse(UUID id, String code, String name, String description,
        Integer standardSessionCount, BigDecimal tuitionFee, boolean active) {
}
