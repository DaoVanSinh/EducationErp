package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một khoá trong combo, kèm học phí gốc đã snapshot lúc tạo (spec mục 4). {@code courseName} và
 * {@code classCode} là dữ liệu hiển thị tra lúc đọc, null khi không tra được lớp của ghi danh.
 */
public record ComboEnrollmentResponse(UUID id, UUID enrollmentId, UUID courseId, String courseName,
        String classCode, BigDecimal originalTuitionFee) {
}
