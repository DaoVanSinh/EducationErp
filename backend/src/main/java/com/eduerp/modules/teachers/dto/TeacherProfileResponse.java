package com.eduerp.modules.teachers.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * {@code payConfigured} chỉ cho biết đã thiết lập lương hay chưa, không lộ mức lương. {@code active} false là đã nghỉ,
 * {@code lastWorkDate} là ngày nghỉ.
 */
public record TeacherProfileResponse(UUID id, UUID accountId, String fullName, String email,
        List<String> subjects, String phone, String bio, boolean active, boolean payConfigured,
        UUID homeBranchId, String homeBranchName, LocalDate lastWorkDate) {
}
