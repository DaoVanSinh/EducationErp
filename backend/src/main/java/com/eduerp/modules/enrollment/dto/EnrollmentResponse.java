package com.eduerp.modules.enrollment.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.time.Instant;
import java.util.UUID;

/**
 * Tên field là hợp đồng với Zod schema ở frontend (entities/enrollment) - đổi tên là breaking.
 * {@code studentName}, {@code classCode}, {@code courseName} là dữ liệu hiển thị tra lúc đọc, null khi
 * không tra được.
 */
public record EnrollmentResponse(UUID id, UUID studentProfileId, String studentName, UUID classId, String classCode,
        UUID courseId, String courseName, UUID branchId, EnrollmentConstants.EnrollmentStatus status,
        Instant enrolledAt, Instant withdrawnAt) {
}
