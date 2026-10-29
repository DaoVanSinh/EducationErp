package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.dto.StudentAcademicSummaryResponse;
import com.eduerp.modules.academic.dto.StudentClassSummaryRow;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.academic.internal.rules.GradeRules;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Điểm/tiến trình của MỘT học viên, gộp xuyên MỌI lớp đã ghi danh - cho trang chi tiết học viên.
 * Hợp đồng ngược chiều với {@code GetClassGradebook}/{@code GetClassProgress} (những usecase đó
 * cho MỌI học viên của MỘT lớp).
 *
 * <p><b>Khác biệt quan trọng nhất với hai usecase kia</b>: ở đó một lớp người gọi không được phép
 * xem là 403 cho CẢ request (đúng, vì request chỉ hỏi về đúng một lớp). Ở đây một học viên có thể
 * học nhiều lớp của nhiều giáo viên khác nhau - một giáo viên xem hồ sơ học viên chỉ được thấy ĐÚNG
 * các lớp của mình dạy, các lớp còn lại phải BỎ QUA lặng lẽ qua {@link ClassGradeAccessGuard#canAccess}
 * (không ném), không được làm hỏng cả response vì một lớp không-phải-của-mình lẫn trong đó.
 *
 * <p>Điểm cuối dùng lại {@code GradeRules.finalScore}, tiến trình dùng lại
 * {@code ProgressRules.countElapsedSessions}/{@code progressPercent} - cùng công thức với bảng
 * điểm/bảng tiến trình một lớp, không phát minh phép tính thứ hai.
 */
@Service
public class GetStudentAcademicSummary {

    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final ClassGradeAccessGuard guard;
    private final GradeEntryRepository gradeEntries;
    private final Clock clock;

    @Autowired
    GetStudentAcademicSummary(EnrollmentManagement enrollments, CoursesManagement courses,
            ClassGradeAccessGuard guard, GradeEntryRepository gradeEntries) {
        this(enrollments, courses, guard, gradeEntries, Clock.systemUTC());
    }

    /** Constructor cho test: tiến trình phụ thuộc "bây giờ" nên phải chốt được Clock (mirror
     * {@code GetClassProgress}). */
    GetStudentAcademicSummary(EnrollmentManagement enrollments, CoursesManagement courses,
            ClassGradeAccessGuard guard, GradeEntryRepository gradeEntries, Clock clock) {
        this.enrollments = enrollments;
        this.courses = courses;
        this.guard = guard;
        this.gradeEntries = gradeEntries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StudentAcademicSummaryResponse execute(UUID actorAccountId, UUID studentProfileId) {
        var now = clock.instant();
        var accessible = enrollments.listEnrollmentsByStudent(studentProfileId).stream()
                .map(entry -> toAccessibleEntry(actorAccountId, entry))
                .filter(Objects::nonNull)
                .toList();

        var classIds = accessible.stream().map(a -> a.entry().classId()).toList();
        var classSummaries = courses.classSummariesOf(classIds);

        var rows = accessible.stream().map(a -> toRow(a, classSummaries, now)).toList();
        return new StudentAcademicSummaryResponse(studentProfileId, rows);
    }

    private record AccessibleEntry(EnrollmentManagement.StudentEnrollmentEntry entry,
            CoursesManagement.ClassInfoResponse classInfo) {
    }

    private AccessibleEntry toAccessibleEntry(UUID actorAccountId,
            EnrollmentManagement.StudentEnrollmentEntry entry) {
        var classInfo = courses.getClassInfo(entry.classId()).orElse(null);
        if (classInfo == null || !guard.canAccess(actorAccountId, classInfo, AccessConstants.Actions.READ)) {
            return null;
        }
        return new AccessibleEntry(entry, classInfo);
    }

    private StudentClassSummaryRow toRow(AccessibleEntry accessible,
            Map<UUID, CoursesManagement.ClassBasicSummary> classSummaries, Instant now) {
        var entry = accessible.entry();
        var classInfo = accessible.classInfo();
        var columns = courses.getGradeColumns(classInfo.courseId());
        var scores = gradeEntries.findAllByEnrollmentIdIn(List.of(entry.enrollmentId())).stream()
                .collect(Collectors.toMap(GradeEntry::getCourseGradeColumnId, GradeEntry::getScore));
        var finalScore = GradeRules.finalScore(columns, scores);

        var elapsedSessions = ProgressRules.countAttendedSessions(classInfo.schedule(), entry.enrolledAt(),
                endMarkOf(entry, now), classInfo.startedAt(), classInfo.endedAt(), ProgressRules.centerZone());
        var progressPercent = ProgressRules.progressPercent(elapsedSessions, classInfo.standardSessionCount());

        var classSummary = classSummaries.get(entry.classId());
        return new StudentClassSummaryRow(entry.classId(),
                classSummary == null ? null : classSummary.code(),
                classSummary == null ? null : classSummary.courseName(),
                entry.status(), finalScore, progressPercent);
    }

    /** Mirror {@code GetClassProgress.endMarkOf}: mốc kết thúc là {@code withdrawnAt} nếu đã rút,
     * ngược lại là "bây giờ". */
    private static Instant endMarkOf(EnrollmentManagement.StudentEnrollmentEntry entry, Instant now) {
        if (entry.status() == EnrollmentConstants.EnrollmentStatus.WITHDRAWN && entry.withdrawnAt() != null) {
            return entry.withdrawnAt();
        }
        return now;
    }
}
