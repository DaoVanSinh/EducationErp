package com.eduerp.modules.landing.dto;

import com.eduerp.modules.landing.LandingConstants;
import java.time.Instant;
import java.util.UUID;

public record ConsultationItemResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        UUID targetCourseId,
        String targetCourseName,
        UUID targetBranchId,
        String targetBranchName,
        String notes,
        LandingConstants.ConsultationStatus status,
        Instant createdAt) {
}
