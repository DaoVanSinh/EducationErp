package com.eduerp.modules.students.dto;

import java.time.LocalDate;
import java.util.UUID;

public record StudentProfileResponse(UUID id, UUID accountId, String fullName, String email,
        LocalDate dateOfBirth, String phone, String sourceChannel, boolean active) {
}
