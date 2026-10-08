package com.eduerp.modules.students.dto;

import java.time.LocalDate;

public record UpdateStudentProfileRequest(LocalDate dateOfBirth, String phone, String sourceChannel,
        boolean active) {
}
