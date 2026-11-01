package com.eduerp.modules.billing.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Tên field là hợp đồng với Zod schema ở frontend (entities/billing) - đổi tên là breaking. */
public record GroupRegistrationResponse(UUID id, UUID courseId, UUID branchId, BigDecimal discountPercent,
        Instant createdAt, List<GroupRegistrationMemberResponse> members) {

    public GroupRegistrationResponse {
        members = List.copyOf(members);
    }
}
