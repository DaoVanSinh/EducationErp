package com.eduerp.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * @param acceptScheduleConflict nhân viên đã xem cảnh báo trùng lịch và vẫn muốn ghi danh; mặc định {@code false}
 */
public record CreateEnrollmentRequest(@NotNull UUID studentProfileId, @NotNull UUID classId,
        boolean acceptScheduleConflict) {

    public CreateEnrollmentRequest(UUID studentProfileId, UUID classId) {
        this(studentProfileId, classId, false);
    }
}
