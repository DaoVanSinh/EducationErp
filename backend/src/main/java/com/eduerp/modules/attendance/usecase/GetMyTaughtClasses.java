package com.eduerp.modules.attendance.usecase;

import com.eduerp.modules.attendance.dto.TaughtClassResponse;
import com.eduerp.modules.attendance.internal.repository.AttendanceSessionRepository;
import com.eduerp.modules.courses.CoursesManagement;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lịch sử giảng dạy của chính giáo viên: vẫn xem được sau khi đã bàn giao lớp hoặc đã nghỉ. */
@Service
public class GetMyTaughtClasses {

    private final AttendanceSessionRepository sessions;
    private final CoursesManagement courses;

    GetMyTaughtClasses(AttendanceSessionRepository sessions, CoursesManagement courses) {
        this.sessions = sessions;
        this.courses = courses;
    }

    @Transactional(readOnly = true)
    public List<TaughtClassResponse> execute(UUID teacherAccountId) {
        var rows = sessions.summarizeTaughtClasses(teacherAccountId);
        var summaries = courses.classSummariesOf(rows.stream().map(row -> row.getClassId()).toList());
        return rows.stream().map(row -> {
            var summary = summaries.get(row.getClassId());
            return new TaughtClassResponse(row.getClassId(), summary == null ? null : summary.code(),
                    summary == null ? null : summary.courseName(), Math.toIntExact(row.getSessionCount()),
                    Math.toIntExact(row.getTotalMinutes()), row.getFirstSessionDate(), row.getLastSessionDate());
        }).toList();
    }
}
