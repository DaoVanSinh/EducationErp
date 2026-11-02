package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Một người trong nhóm: học phí gốc và học phí sau giảm (trần tổng hoá đơn của ghi danh đó).
 * {@code studentName} null khi không tra được họ tên.
 */
public record GroupRegistrationMemberResponse(UUID enrollmentId, UUID studentProfileId, String studentName,
        BigDecimal originalTuitionFee, BigDecimal discountedTuitionFee) {
}
