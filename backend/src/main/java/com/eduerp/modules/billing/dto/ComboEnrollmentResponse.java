package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Một khoá trong combo, kèm học phí gốc đã snapshot lúc tạo (spec mục 4). */
public record ComboEnrollmentResponse(UUID id, UUID enrollmentId, UUID courseId,
        BigDecimal originalTuitionFee) {
}
