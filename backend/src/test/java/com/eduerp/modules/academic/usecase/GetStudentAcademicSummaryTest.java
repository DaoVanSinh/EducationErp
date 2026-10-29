package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.courses.TestClassInfos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Review Focus (mirror GetClassProgressTest): lọc theo quyền KHÔNG ném lỗi (một lớp người xem
 * không được phép thấy phải bị bỏ qua lặng lẽ, không làm hỏng cả response), điểm/tiến trình tính
 * đúng công thức đã có của GradeRules/ProgressRules, và `null` khi chưa đủ dữ liệu - không bao giờ
 * ném cho một học viên có lớp mà người xem (giáo viên) không được phép thấy.
 */
class GetStudentAcademicSummaryTest {

    private static final ZoneId ZONE = ProgressRules.centerZone();

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant();
    }

    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final ClassGradeAccessGuard guard = mock(ClassGradeAccessGuard.class);
    private final GradeEntryRepository gradeEntries = mock(GradeEntryRepository.class);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID studentProfileId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();

    private GetStudentAcademicSummary useCaseAt(Instant now) {
        return new GetStudentAcademicSummary(enrollments, courses, guard, gradeEntries,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private EnrollmentManagement.StudentEnrollmentEntry entry(UUID classId, UUID courseId,
            EnrollmentConstants.EnrollmentStatus status, Instant enrolledAt, Instant withdrawnAt) {
        return new EnrollmentManagement.StudentEnrollmentEntry(enrollmentId, classId, courseId, status,
                enrolledAt, withdrawnAt);
    }

    private CoursesManagement.ClassInfoResponse classInfo(UUID teacherId, Integer standardSessionCount,
            List<WeeklyScheduleSlot> schedule) {
        return TestClassInfos.of(classId, courseId, UUID.randomUUID(), teacherId, 20,
                true, standardSessionCount, schedule);
    }

    private static final List<WeeklyScheduleSlot> MON_WED_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)),
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    @Test
    void returnsAnEmptyListForAStudentWithoutAnyEnrollment() {
        when(enrollments.listEnrollmentsByStudent(studentProfileId)).thenReturn(List.of());

        var summary = useCaseAt(Instant.now()).execute(actorAccountId, studentProfileId);

        assertThat(summary.studentProfileId()).isEqualTo(studentProfileId);
        assertThat(summary.classes()).isEmpty();
    }

    /** Khẳng định quan trọng nhất: một lớp người xem (giáo viên khác) không được phép thấy phải bị
     * BỎ QUA, không ném 403 làm hỏng cả response. */
    @Test
    void skipsAClassTheActorCannotAccessWithoutErroring() {
        var otherTeacherId = UUID.randomUUID();
        when(enrollments.listEnrollmentsByStudent(studentProfileId))
                .thenReturn(List.of(entry(classId, courseId, EnrollmentConstants.EnrollmentStatus.ACTIVE,
                        at(2026, 1, 5, 8, 0), null)));
        var info = classInfo(otherTeacherId, 24, MON_WED_EVENING);
        when(courses.getClassInfo(classId)).thenReturn(java.util.Optional.of(info));
        when(guard.canAccess(actorAccountId, info, AccessConstants.Actions.READ)).thenReturn(false);

        var summary = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, studentProfileId);

        assertThat(summary.classes()).isEmpty();
    }

    @Test
    void includesAClassTheActorCanAccessWithItsCodeAndCourseName() {
        when(enrollments.listEnrollmentsByStudent(studentProfileId))
                .thenReturn(List.of(entry(classId, courseId, EnrollmentConstants.EnrollmentStatus.ACTIVE,
                        at(2026, 1, 5, 8, 0), null)));
        var info = classInfo(actorAccountId, 24, MON_WED_EVENING);
        when(courses.getClassInfo(classId)).thenReturn(java.util.Optional.of(info));
        when(guard.canAccess(actorAccountId, info, AccessConstants.Actions.READ)).thenReturn(true);
        when(courses.getGradeColumns(courseId)).thenReturn(List.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentId))).thenReturn(List.of());
        when(courses.classSummariesOf(List.of(classId))).thenReturn(Map.of(classId,
                new CoursesManagement.ClassBasicSummary(classId, "TA-K1", courseId, "Tiếng Anh")));

        var summary = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, studentProfileId);

        assertThat(summary.classes()).hasSize(1);
        var row = summary.classes().get(0);
        assertThat(row.classId()).isEqualTo(classId);
        assertThat(row.classCode()).isEqualTo("TA-K1");
        assertThat(row.courseName()).isEqualTo("Tiếng Anh");
        assertThat(row.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
    }

    /** Điểm cuối tính đúng công thức GradeRules đã có - không phát minh phép tính mới. */
    @Test
    void computesTheFinalScoreFromTheGradeColumnsAndEntries() {
        var columnId = UUID.randomUUID();
        when(enrollments.listEnrollmentsByStudent(studentProfileId))
                .thenReturn(List.of(entry(classId, courseId, EnrollmentConstants.EnrollmentStatus.ACTIVE,
                        at(2026, 1, 5, 8, 0), null)));
        var info = classInfo(actorAccountId, 24, MON_WED_EVENING);
        when(courses.getClassInfo(classId)).thenReturn(java.util.Optional.of(info));
        when(guard.canAccess(actorAccountId, info, AccessConstants.Actions.READ)).thenReturn(true);
        when(courses.getGradeColumns(courseId))
                .thenReturn(List.of(new GradeColumnResponse(columnId, "Cuối kỳ",
                        new BigDecimal("100.00"), new BigDecimal("10.00"), 0)));
        var gradeEntry = new GradeEntry(enrollmentId, columnId, new BigDecimal("8.00"), actorAccountId);
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentId))).thenReturn(List.of(gradeEntry));
        when(courses.classSummariesOf(any())).thenReturn(Map.of());

        var summary = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, studentProfileId);

        assertThat(summary.classes().get(0).finalScore())
                .as("một cột 100% trọng số, điểm 8/10 -> (8/10)*100 = 80.00")
                .isEqualByComparingTo(new BigDecimal("80.00"));
    }

    /** Ghi danh đã rút đóng băng tiến trình tại withdrawnAt - cùng công thức ProgressRules dùng cho
     * bảng tiến trình một lớp, không phát minh lại. */
    @Test
    void freezesProgressAtWithdrawnAt() {
        when(enrollments.listEnrollmentsByStudent(studentProfileId))
                .thenReturn(List.of(entry(classId, courseId, EnrollmentConstants.EnrollmentStatus.WITHDRAWN,
                        at(2026, 1, 5, 8, 0), at(2026, 1, 13, 9, 0))));
        var info = classInfo(actorAccountId, 24, MON_WED_EVENING);
        when(courses.getClassInfo(classId)).thenReturn(java.util.Optional.of(info));
        when(guard.canAccess(actorAccountId, info, AccessConstants.Actions.READ)).thenReturn(true);
        when(courses.getGradeColumns(courseId)).thenReturn(List.of());
        when(gradeEntries.findAllByEnrollmentIdIn(List.of(enrollmentId))).thenReturn(List.of());
        when(courses.classSummariesOf(any())).thenReturn(Map.of());

        var readMuchLater = useCaseAt(at(2026, 4, 21, 23, 0)).execute(actorAccountId, studentProfileId);

        assertThat(readMuchLater.classes().get(0).progressPercent())
                .as("3 buổi (05,07,12 tháng 1) / 24 = 12.5 -> 13%, đóng băng dù đọc ba tháng sau")
                .isEqualTo(13);
    }
}
