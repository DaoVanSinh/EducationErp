package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record CreateClassRequest(@NotNull UUID courseId, @NotBlank String code, @NotNull UUID branchId,
        @NotNull UUID teacherId, @Positive int maxSeats, @Valid List<WeeklyScheduleSlot> schedule) {
}
