package com.eduerp.modules.teachers.dto;

import java.util.List;
import java.util.UUID;

public record TeacherProfileResponse(UUID id, UUID accountId, String fullName, String email,
        List<String> subjects, String phone, String bio, boolean active) {
}
