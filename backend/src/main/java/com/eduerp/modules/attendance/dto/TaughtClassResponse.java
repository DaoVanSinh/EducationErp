package com.eduerp.modules.attendance.dto;

import java.time.LocalDate;
import java.util.UUID;

/** Một lớp giáo viên từng dạy, tính từ các buổi được ghi giờ cho họ - kể cả lớp đã bàn giao cho người khác. */
public record TaughtClassResponse(UUID classId, String classCode, String courseName, int sessionCount,
        int totalMinutes, LocalDate firstSessionDate, LocalDate lastSessionDate) {
}
