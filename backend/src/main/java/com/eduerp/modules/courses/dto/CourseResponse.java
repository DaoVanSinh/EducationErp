package com.eduerp.modules.courses.dto;

import java.util.UUID;

public record CourseResponse(UUID id, String code, String name, String description,
        Integer standardSessionCount, boolean active) {
}
