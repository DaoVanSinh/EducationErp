package com.eduerp.modules.academic.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.time.Instant;
import java.util.UUID;

/**
 * Một dòng bảng tiến trình. {@code progressPercent} là {@code null} khi
 * {@code standardSessionCount} chưa cấu hình - "chưa cấu hình tổng số buổi chuẩn", không phải 0%
 * (spec mục 5, Review Focus #8). {@code elapsedSessions} vẫn có số thật để người xem hiểu con số
 * null đến từ việc thiếu cấu hình chứ không phải thiếu dữ liệu học.
 */
public record StudentProgressRow(UUID enrollmentId, UUID studentProfileId, String studentName,
        EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt,
        int elapsedSessions, Integer standardSessionCount, Integer progressPercent) {
}
