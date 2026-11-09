package com.eduerp.modules.attendance;

import com.eduerp.modules.attendance.internal.repository.AttendanceSessionRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Facade của module attendance - type duy nhất module khác được gọi. */
@Service
public class AttendanceManagement {

    /** Số buổi đã dạy và tổng thời lượng của một giáo viên trong một khoảng ngày. */
    public record TeachingSummary(UUID teacherAccountId, int sessionCount, int totalMinutes) {
    }

    private final AttendanceSessionRepository sessions;

    AttendanceManagement(AttendanceSessionRepository sessions) {
        this.sessions = sessions;
    }

    /**
     * Giờ dạy thực tế theo giáo viên, cả hai đầu ngày đều tính - nguồn duy nhất cho giờ dạy trên phiếu lương.
     * Giáo viên không có buổi nào chỉ vắng mặt trong map.
     */
    @Transactional(readOnly = true)
    public Map<UUID, TeachingSummary> teachingSummaries(LocalDate from, LocalDate to) {
        return sessions.summarizeTeaching(from, to).stream()
                .map(row -> new TeachingSummary(row.getTeacherAccountId(), Math.toIntExact(row.getSessionCount()),
                        Math.toIntExact(row.getTotalMinutes())))
                .collect(Collectors.toMap(TeachingSummary::teacherAccountId, Function.identity()));
    }
}
