package com.eduerp.modules.enrollment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateEnrollmentRequest(@NotNull UUID studentProfileId, @NotNull UUID classId) {
}
