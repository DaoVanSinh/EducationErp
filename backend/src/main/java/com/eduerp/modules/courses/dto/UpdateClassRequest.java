package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

/** Không có {@code courseId}/{@code branchId}/{@code code}: bất biến sau khi tạo (xem spec §2.2). */
public record UpdateClassRequest(@NotNull UUID teacherId, @Positive int maxSeats, boolean active,
        @Valid List<WeeklyScheduleSlot> schedule) {
}
