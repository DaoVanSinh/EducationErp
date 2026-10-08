package com.eduerp.modules.academic.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Một dòng bảng điểm. {@code scoresByColumnId} khuyết cột nào nghĩa là cột đó CHƯA có điểm, và khi
 * đó {@code finalScore} là {@code null} = "chưa đủ điểm" (spec mục 6) - KHÔNG phải 0. Tên field là
 * hợp đồng với Zod schema ở frontend ({@code entities/academic}) - đổi tên là breaking.
 */
public record StudentGradesRow(UUID enrollmentId, UUID studentProfileId, String studentName,
        EnrollmentConstants.EnrollmentStatus status, Map<UUID, BigDecimal> scoresByColumnId,
        BigDecimal finalScore) {
}
