package com.eduerp.modules.teachers.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateTeacherProfileRequest(@NotNull List<String> subjects, String phone, String bio, boolean active) {
}
