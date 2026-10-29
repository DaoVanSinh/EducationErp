package com.eduerp.modules.attendance.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.util.List;
import java.util.UUID;

/** Điểm danh của CHÍNH học viên ở một lớp đã ghi danh - buổi mới nhất đứng trước. {@code classCode}/
 * {@code courseName} null khi lớp gốc đã bị xoá. */
public record MyAttendanceClassResponse(UUID enrollmentId, UUID classId, String classCode, String courseName,
        EnrollmentConstants.EnrollmentStatus enrollmentStatus, AttendanceSummary summary,
        List<MyAttendanceItem> sessions) {
}
