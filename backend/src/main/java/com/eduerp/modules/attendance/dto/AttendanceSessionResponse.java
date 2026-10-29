package com.eduerp.modules.attendance.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Một buổi đã điểm danh của lớp, kèm số học viên theo từng trạng thái. */
public record AttendanceSessionResponse(UUID sessionId, LocalDate sessionDate, String note,
        AttendanceSummary summary) {
}
