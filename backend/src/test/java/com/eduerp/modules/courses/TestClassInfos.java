package com.eduerp.modules.courses;

import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dựng {@link CoursesManagement.ClassInfoResponse} cho unit test theo hình dạng CŨ (trước V31) - cờ
 * {@code active} được ánh xạ đúng như migration V31 làm với dữ liệu thật: {@code true} → lớp đang học (khai
 * giảng từ rất lâu, nên phép đếm tiến trình bắt đầu đúng từ ngày ghi danh như trước), {@code false} → lớp đã
 * kết thúc. Giữ nguyên ý nghĩa của các test viết trước khi có vòng đời lớp.
 */
public final class TestClassInfos {

    private TestClassInfos() {
    }

    public static CoursesManagement.ClassInfoResponse of(UUID classId, UUID courseId, UUID branchId, UUID teacherId,
            int maxSeats, boolean active, Integer standardSessionCount, List<WeeklyScheduleSlot> schedule) {
        var status = active ? CoursesConstants.ClassStatus.IN_PROGRESS : CoursesConstants.ClassStatus.COMPLETED;
        return new CoursesManagement.ClassInfoResponse(classId, courseId, branchId, teacherId, maxSeats, 1, status,
                LocalDate.EPOCH, null, Instant.EPOCH, active ? null : Instant.EPOCH.plusSeconds(1),
                standardSessionCount, schedule);
    }

    public static CoursesManagement.ClassInfoResponse withStatus(UUID classId, UUID courseId, UUID branchId,
            UUID teacherId, CoursesConstants.ClassStatus status) {
        var started = status == CoursesConstants.ClassStatus.ENROLLING ? null : Instant.EPOCH;
        return new CoursesManagement.ClassInfoResponse(classId, courseId, branchId, teacherId, 20, 1, status,
                LocalDate.now(), null, started, null, 24, List.of());
    }
}
