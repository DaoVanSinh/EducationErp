package com.eduerp.modules.attendance.dto;

import com.eduerp.modules.attendance.AttendanceConstants;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Trạng thái của MỘT học viên trong buổi điểm danh. */
public record AttendanceEntryRequest(@NotNull UUID enrollmentId,
        @NotNull AttendanceConstants.AttendanceStatus status,
        @Size(max = AttendanceConstants.Limits.MAX_NOTE_LENGTH) String note) {
}
