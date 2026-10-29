package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param minSeats số học viên tối thiểu để khai giảng (1..maxSeats)
 * @param startDate ngày khai giảng dự kiến, không trước hôm nay
 * @param endDate ngày kết thúc dự kiến, không bắt buộc
 */
public record CreateClassRequest(@NotNull UUID courseId, @NotBlank String code, @NotNull UUID branchId,
        @NotNull UUID teacherId, @Positive int maxSeats, @Positive int minSeats, @NotNull LocalDate startDate,
        LocalDate endDate, @NotNull @Valid List<WeeklyScheduleSlot> schedule) {
}
