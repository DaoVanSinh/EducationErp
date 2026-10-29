package com.eduerp.modules.attendance.dto;

import com.eduerp.modules.attendance.AttendanceConstants;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.util.Map;
import java.util.UUID;

/**
 * Một dòng của bảng điểm danh: trạng thái của học viên ở từng buổi. Khuyết khoá {@code sessionId}
 * nghĩa là buổi đó chưa ghi nhận học viên này (ví dụ ghi danh sau buổi đó).
 */
public record StudentAttendanceRow(UUID enrollmentId, UUID studentProfileId, String studentName,
        EnrollmentConstants.EnrollmentStatus enrollmentStatus,
        Map<UUID, AttendanceConstants.AttendanceStatus> statusesBySessionId,
        Map<UUID, String> notesBySessionId, AttendanceSummary summary) {
}
