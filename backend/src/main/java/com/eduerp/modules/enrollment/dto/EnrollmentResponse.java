package com.eduerp.modules.enrollment.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.time.Instant;
import java.util.UUID;

/** Tên field là hợp đồng với Zod schema ở frontend (entities/enrollment) - đổi tên là breaking. */
public record EnrollmentResponse(UUID id, UUID studentProfileId, UUID classId, UUID courseId, UUID branchId,
        EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt) {
}
