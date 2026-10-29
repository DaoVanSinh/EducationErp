package com.eduerp.modules.attendance.usecase;

import com.eduerp.modules.attendance.dto.MyAttendanceClassResponse;
import com.eduerp.modules.attendance.dto.MyAttendanceItem;
import com.eduerp.modules.attendance.internal.model.AttendanceRecord;
import com.eduerp.modules.attendance.internal.repository.AttendanceRecordRepository;
import com.eduerp.modules.attendance.internal.rules.AttendanceRules;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng học tập: điểm danh của CHÍNH học viên đang đăng nhập ở mọi lớp đã ghi danh. Không nhận id nào từ
 * client - hồ sơ tra từ {@code accountId} của phiên, nên không có đường xem dữ liệu người khác.
 */
@Service
public class GetMyAttendance {

    private final StudentsManagement students;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final AttendanceRecordRepository records;

    GetMyAttendance(StudentsManagement students, EnrollmentManagement enrollments, CoursesManagement courses,
            AttendanceRecordRepository records) {
        this.students = students;
        this.enrollments = enrollments;
        this.courses = courses;
        this.records = records;
    }

    @Transactional(readOnly = true)
    public List<MyAttendanceClassResponse> execute(UUID accountId) {
        var profile = students.findProfileByAccountId(accountId).orElse(null);
        if (profile == null) {
            return List.of();
        }
        var entries = enrollments.listEnrollmentsByStudent(profile.studentProfileId());
        if (entries.isEmpty()) {
            return List.of();
        }
        var summaries = courses.classSummariesOf(entries.stream()
                .map(EnrollmentManagement.StudentEnrollmentEntry::classId).distinct().toList());
        Map<UUID, List<AttendanceRecord>> byEnrollment = records.findAllByEnrollmentIdIn(entries.stream()
                        .map(EnrollmentManagement.StudentEnrollmentEntry::enrollmentId).toList())
                .stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getEnrollmentId));

        return entries.stream()
                .map(entry -> toResponse(entry, summaries.get(entry.classId()),
                        byEnrollment.getOrDefault(entry.enrollmentId(), List.of())))
                .toList();
    }

    private static MyAttendanceClassResponse toResponse(EnrollmentManagement.StudentEnrollmentEntry entry,
            CoursesManagement.ClassBasicSummary summary, List<AttendanceRecord> classRecords) {
        var items = classRecords.stream()
                .map(record -> new MyAttendanceItem(record.getSession().getSessionDate(), record.getStatus(),
                        record.getNote()))
                .sorted(Comparator.comparing(MyAttendanceItem::sessionDate).reversed())
                .toList();
        return new MyAttendanceClassResponse(entry.enrollmentId(), entry.classId(),
                summary == null ? null : summary.code(), summary == null ? null : summary.courseName(),
                entry.status(),
                AttendanceRules.summarize(classRecords.stream().map(AttendanceRecord::getStatus).toList()),
                items);
    }
}
