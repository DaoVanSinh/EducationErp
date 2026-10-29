package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.academic.dto.MyGradeItem;
import com.eduerp.modules.academic.dto.MyLearningClassResponse;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.academic.internal.rules.GradeRules;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng học tập: mọi lớp mà CHÍNH học viên đang đăng nhập đã ghi danh, kèm lịch học, điểm từng cột,
 * điểm tổng kết và tiến trình (đề cương ĐATN - học viên tự tra cứu thay vì phải hỏi trung tâm).
 *
 * <p>Không đi qua {@code ClassGradeAccessGuard}: guard đó trả lời "người gọi có được xem lớp NÀY
 * không" cho giáo viên/admin. Ở đây câu hỏi khác - học viên chỉ đọc đúng các ghi danh của hồ sơ gắn
 * với chính {@code accountId} của mình, không bao giờ nhận id từ client, nên không có đường xem dữ
 * liệu người khác.
 *
 * <p>Điểm tổng kết dùng lại {@code GradeRules.finalScore}, tiến trình dùng lại {@code ProgressRules} -
 * cùng công thức với sổ điểm giáo viên thấy, không phát minh phép tính thứ hai.
 */
@Service
public class GetMyLearning {

    private final StudentsManagement students;
    private final EnrollmentManagement enrollments;
    private final CoursesManagement courses;
    private final IdentityManagement identity;
    private final GradeEntryRepository gradeEntries;
    private final Clock clock;

    @Autowired
    GetMyLearning(StudentsManagement students, EnrollmentManagement enrollments, CoursesManagement courses,
            IdentityManagement identity, GradeEntryRepository gradeEntries) {
        this(students, enrollments, courses, identity, gradeEntries, Clock.systemUTC());
    }

    /** Constructor cho test: tiến trình phụ thuộc "bây giờ" nên phải chốt được Clock. */
    GetMyLearning(StudentsManagement students, EnrollmentManagement enrollments, CoursesManagement courses,
            IdentityManagement identity, GradeEntryRepository gradeEntries, Clock clock) {
        this.students = students;
        this.enrollments = enrollments;
        this.courses = courses;
        this.identity = identity;
        this.gradeEntries = gradeEntries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MyLearningClassResponse> execute(UUID accountId) {
        var profile = students.findProfileByAccountId(accountId).orElse(null);
        if (profile == null) {
            return List.of();
        }
        var entries = enrollments.listEnrollmentsByStudent(profile.studentProfileId());
        if (entries.isEmpty()) {
            return List.of();
        }

        var classInfos = entries.stream()
                .map(entry -> courses.getClassInfo(entry.classId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(CoursesManagement.ClassInfoResponse::classId, info -> info, (a, b) -> a));
        var context = new Context(
                courses.classSummariesOf(classInfos.keySet()),
                identity.summariesOf(classInfos.values().stream()
                        .map(CoursesManagement.ClassInfoResponse::teacherId).distinct().toList()),
                scoresByEnrollment(entries),
                new HashMap<>(),
                clock.instant());

        return entries.stream()
                .filter(entry -> classInfos.containsKey(entry.classId()))
                .map(entry -> toResponse(entry, classInfos.get(entry.classId()), context))
                .toList();
    }

    /** Dữ liệu nạp theo lô một lần cho cả request - tránh N+1 khi học viên học nhiều lớp. */
    private record Context(Map<UUID, CoursesManagement.ClassBasicSummary> classSummaries,
            Map<UUID, IdentityManagement.AccountBasicInfo> teachers,
            Map<UUID, Map<UUID, BigDecimal>> scores,
            Map<UUID, List<GradeColumnResponse>> columnsByCourse,
            Instant now) {
    }

    private Map<UUID, Map<UUID, BigDecimal>> scoresByEnrollment(
            List<EnrollmentManagement.StudentEnrollmentEntry> entries) {
        var enrollmentIds = entries.stream().map(EnrollmentManagement.StudentEnrollmentEntry::enrollmentId).toList();
        return gradeEntries.findAllByEnrollmentIdIn(enrollmentIds).stream()
                .collect(Collectors.groupingBy(GradeEntry::getEnrollmentId,
                        Collectors.toMap(GradeEntry::getCourseGradeColumnId, GradeEntry::getScore)));
    }

    private MyLearningClassResponse toResponse(EnrollmentManagement.StudentEnrollmentEntry entry,
            CoursesManagement.ClassInfoResponse classInfo, Context context) {
        var columns = context.columnsByCourse()
                .computeIfAbsent(classInfo.courseId(), courses::getGradeColumns);
        var scores = context.scores().getOrDefault(entry.enrollmentId(), Map.of());
        var grades = columns.stream()
                .map(column -> new MyGradeItem(column.id(), column.name(), column.weightPercent(),
                        column.maxScore(), scores.get(column.id())))
                .toList();

        var elapsed = ProgressRules.countAttendedSessions(classInfo.schedule(), entry.enrolledAt(),
                endMarkOf(entry, context.now()), classInfo.startedAt(), classInfo.endedAt(),
                ProgressRules.centerZone());
        var summary = context.classSummaries().get(entry.classId());
        var teacher = context.teachers().get(classInfo.teacherId());

        return new MyLearningClassResponse(entry.enrollmentId(), entry.classId(),
                summary == null ? null : summary.code(), classInfo.courseId(),
                summary == null ? null : summary.courseName(),
                teacher == null ? null : teacher.fullName(),
                entry.status(), entry.enrolledAt(), classInfo.schedule(), grades,
                GradeRules.finalScore(columns, scores), elapsed, classInfo.standardSessionCount(),
                ProgressRules.progressPercent(elapsed, classInfo.standardSessionCount()), classInfo.status(),
                classInfo.startDate(), classInfo.endDate());
    }

    /** Mirror {@code GetStudentAcademicSummary.endMarkOf}: rút khỏi lớp thì tiến trình đóng băng. */
    private static Instant endMarkOf(EnrollmentManagement.StudentEnrollmentEntry entry, Instant now) {
        if (entry.status() == EnrollmentConstants.EnrollmentStatus.WITHDRAWN && entry.withdrawnAt() != null) {
            return entry.withdrawnAt();
        }
        return now;
    }
}
