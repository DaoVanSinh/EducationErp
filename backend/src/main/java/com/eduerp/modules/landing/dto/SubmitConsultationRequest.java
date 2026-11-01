package com.eduerp.modules.landing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SubmitConsultationRequest(
        @NotBlank @Size(max = 160) String fullName,
        @NotBlank @Size(max = 32) String phone,
        @Size(max = 160) String email,
        UUID targetCourseId,
        UUID targetBranchId,
        @Size(max = 1000) String notes) {
}
