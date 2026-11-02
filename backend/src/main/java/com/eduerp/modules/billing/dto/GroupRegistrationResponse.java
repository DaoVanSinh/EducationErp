package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking.
 * {@code courseName} null khi không tra được tên khoá.
 */
public record GroupRegistrationResponse(UUID id, UUID courseId, String courseName, UUID branchId,
        BigDecimal discountPercent, Instant createdAt, List<GroupRegistrationMemberResponse> members) {

    public GroupRegistrationResponse {
        members = List.copyOf(members);
    }
}
