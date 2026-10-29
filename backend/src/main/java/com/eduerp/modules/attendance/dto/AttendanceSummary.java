package com.eduerp.modules.attendance.dto;

/**
 * Thống kê chuyên cần. {@code attendancePercent} = (có mặt + đi muộn) / (có mặt + đi muộn + vắng),
 * vắng có phép không tính vào mẫu số; {@code null} khi chưa có buổi nào được tính - KHÔNG phải 0%.
 */
public record AttendanceSummary(int presentCount, int lateCount, int absentCount, int excusedCount,
        Integer attendancePercent) {
}
