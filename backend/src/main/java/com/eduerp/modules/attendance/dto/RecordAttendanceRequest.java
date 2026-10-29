package com.eduerp.modules.attendance.dto;

import com.eduerp.modules.attendance.AttendanceConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Điểm danh một buổi. Upsert theo {@code (classId, sessionDate)}: gửi lại cùng ngày là SỬA buổi đó.
 * {@code entries} chỉ cần chứa những học viên muốn ghi nhận - học viên không có trong danh sách giữ
 * nguyên trạng thái cũ (nếu có).
 */
public record RecordAttendanceRequest(@NotNull UUID classId, @NotNull LocalDate sessionDate,
        @Size(max = AttendanceConstants.Limits.MAX_NOTE_LENGTH) String note,
        @NotEmpty List<@Valid @NotNull AttendanceEntryRequest> entries) {
}
