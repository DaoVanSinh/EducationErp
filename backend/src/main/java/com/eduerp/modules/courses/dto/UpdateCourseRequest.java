package com.eduerp.modules.courses.dto;

import jakarta.validation.constraints.NotBlank;

/** Không có {@code code}: mã khóa học bất biến sau khi tạo. */
public record UpdateCourseRequest(@NotBlank String name, String description,
        Integer standardSessionCount, boolean active) {
}
