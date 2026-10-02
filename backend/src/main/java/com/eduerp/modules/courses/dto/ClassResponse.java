package com.eduerp.modules.courses.dto;

import java.util.List;
import java.util.UUID;

public record ClassResponse(UUID id, String code, UUID courseId, String courseName, UUID branchId,
        String branchName, UUID teacherId, String teacherName, int maxSeats, boolean active,
        List<WeeklyScheduleSlot> schedule) {
}
