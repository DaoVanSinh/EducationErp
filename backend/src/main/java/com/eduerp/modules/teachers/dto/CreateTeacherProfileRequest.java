package com.eduerp.modules.teachers.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateTeacherProfileRequest(@NotNull UUID accountId, @NotNull List<String> subjects, String phone,
        String bio) {
}
