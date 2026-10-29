package com.eduerp.modules.academic.usecase;

import com.eduerp.modules.courses.TestClassInfos;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.internal.model.GradeEntry;
import com.eduerp.modules.academic.internal.repository.GradeEntryRepository;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.students.StudentsManagement;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Cổng học tập: học viên chỉ thấy ghi danh của hồ sơ gắn với chính tài khoản mình, với điểm/tiến
 * trình tính bằng ĐÚNG công thức sổ điểm giáo viên dùng.
 */
class GetMyLearningTest {

    private final StudentsManagement students = mock(StudentsManagement.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final CoursesManagement courses = mock(CoursesManagement.class);
    private final IdentityManagement identity = mock(IdentityManagement.class);
    private final GradeEntryRepository gradeEntries = mock(GradeEntryRepository.class);

    private final UUID accountId = UUID.randomUUID();
    private final UUID profileId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID midtermId = UUID.randomUUID();
    private final UUID finalId = UUID.randomUUID();

    private static final List<WeeklyScheduleSlot> MON_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    private static Instant at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0).atZone(ProgressRules.centerZone()).toInstant();
    }

    private GetMyLearning useCaseAt(Instant now) {
        return new GetMyLearning(students, enrollments, courses, identity, gradeEntries, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void stubOneEnrollment() {
        when(students.findProfileByAccountId(accountId))
                .thenReturn(Optional.of(new StudentsManagement.StudentSummaryResponse(profileId, accountId, true)));
        when(enrollments.listEnrollmentsByStudent(profileId)).thenReturn(List.of(
                new EnrollmentManagement.StudentEnrollmentEntry(enrollmentId, classId, courseId,
                        EnrollmentConstants.EnrollmentStatus.ACTIVE, at(2026, 1, 1, 8), null)));
        when(courses.getClassInfo(classId)).thenReturn(Optional.of(TestClassInfos.of(
                classId, courseId, UUID.randomUUID(), teacherId, 20, true, 10, MON_EVENING)));
        when(courses.classSummariesOf(anyCollection())).thenReturn(Map.of(classId,
                new CoursesManagement.ClassBasicSummary(classId, "A201", courseId, "TOEIC")));
        when(identity.summariesOf(anyCollection())).thenReturn(Map.of(teacherId,
                new IdentityManagement.AccountBasicInfo(teacherId, "gv@eduerp.local", "Cô Lan")));
        when(courses.getGradeColumns(courseId)).thenReturn(List.of(
                new GradeColumnResponse(midtermId, "Giữa kỳ", new BigDecimal("40"), new BigDecimal("10"), 0),
                new GradeColumnResponse(finalId, "Cuối kỳ", new BigDecimal("60"), new BigDecimal("10"), 1)));
    }

    @Test
    void returnsNothingForAnAccountWithoutAStudentProfile() {
        when(students.findProfileByAccountId(accountId)).thenReturn(Optional.empty());

        assertThat(useCaseAt(Instant.now()).execute(accountId)).isEmpty();
        verifyNoInteractions(enrollments, gradeEntries);
    }

    /** Thiếu một cột điểm thì tổng kết null, nhưng cột đã chấm vẫn phải hiện điểm. */
    @Test
    void showsScoredColumnsAndLeavesFinalScoreEmptyUntilEveryColumnIsScored() {
        stubOneEnrollment();
        when(gradeEntries.findAllByEnrollmentIdIn(anyCollection()))
                .thenReturn(List.of(new GradeEntry(enrollmentId, midtermId, new BigDecimal("8"), teacherId)));

        var result = useCaseAt(at(2026, 1, 20, 23)).execute(accountId);

        assertThat(result).hasSize(1);
        var row = result.get(0);
        assertThat(row.classCode()).isEqualTo("A201");
        assertThat(row.courseName()).isEqualTo("TOEIC");
        assertThat(row.teacherName()).isEqualTo("Cô Lan");
        assertThat(row.schedule()).isEqualTo(MON_EVENING);
        assertThat(row.grades()).extracting(g -> g.score() == null ? null : g.score().intValue())
                .containsExactly(8, null);
        assertThat(row.finalScore()).isNull();
        // Thứ Hai 5, 12, 19/01/2026 lúc 18:00 đều rơi vào [01/01 08:00, 20/01 23:00].
        assertThat(row.elapsedSessions()).isEqualTo(3);
        assertThat(row.progressPercent()).isEqualTo(30);
    }

    @Test
    void computesTheFinalScoreWithTheSameWeightedFormulaAsTheGradebook() {
        stubOneEnrollment();
        when(gradeEntries.findAllByEnrollmentIdIn(anyCollection())).thenReturn(List.of(
                new GradeEntry(enrollmentId, midtermId, new BigDecimal("8"), teacherId),
                new GradeEntry(enrollmentId, finalId, new BigDecimal("9"), teacherId)));

        var row = useCaseAt(at(2026, 1, 20, 23)).execute(accountId).get(0);

        // 8/10*40 + 9/10*60 = 86
        assertThat(row.finalScore()).isEqualByComparingTo("86");
    }
}
