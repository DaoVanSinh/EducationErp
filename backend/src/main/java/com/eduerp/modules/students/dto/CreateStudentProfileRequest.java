package com.eduerp.modules.students.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateStudentProfileRequest(@NotNull UUID accountId, LocalDate dateOfBirth, String phone,
        String sourceChannel) {
}
