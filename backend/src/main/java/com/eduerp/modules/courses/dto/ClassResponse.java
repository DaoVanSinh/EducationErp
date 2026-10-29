package com.eduerp.modules.courses.dto;

import com.eduerp.modules.courses.CoursesConstants;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param activeEnrollments số học viên đang học - để màn hình hiện "8/20 (tối thiểu 10)" và biết lớp đã đủ
 *        điều kiện khai giảng chưa
 */
public record ClassResponse(UUID id, String code, UUID courseId, String courseName, UUID branchId,
        String branchName, UUID teacherId, String teacherName, int maxSeats, int minSeats, long activeEnrollments,
        CoursesConstants.ClassStatus status, LocalDate startDate, LocalDate endDate, Instant startedAt,
        Instant endedAt, List<WeeklyScheduleSlot> schedule) {
}
