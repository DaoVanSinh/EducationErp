package com.eduerp.modules.courses.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Không có {@code courseId}/{@code branchId}/{@code code}: bất biến sau khi tạo (xem spec §2.2). Không có
 * trạng thái: lớp chuyển trạng thái qua các thao tác riêng (khai giảng/kết thúc/huỷ). {@code minSeats} và
 * {@code startDate} chỉ đổi được khi lớp còn đang tuyển sinh.
 */
public record UpdateClassRequest(@NotNull UUID teacherId, @Positive int maxSeats, @Positive int minSeats,
        @NotNull LocalDate startDate, LocalDate endDate, @NotNull @Valid List<WeeklyScheduleSlot> schedule) {
}
