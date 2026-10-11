package com.eduerp.modules.academic.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.modules.academic.NotAssignedToClassException;
import com.eduerp.modules.academic.dto.StudentProgressRow;
import com.eduerp.modules.academic.internal.StudentNameResolver;
import com.eduerp.modules.academic.internal.access.ClassGradeAccessGuard;
import com.eduerp.modules.academic.internal.rules.ProgressRules;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.CoursesManagement;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
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

class GetClassProgressTest {

    private static final ZoneId ZONE = ProgressRules.centerZone();

    private static final List<WeeklyScheduleSlot> MON_WED_EVENING = List.of(
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)),
            new WeeklyScheduleSlot(CoursesConstants.DayOfWeek.WED, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    private static Instant at(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).atZone(ZONE).toInstant();
    }

    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final ClassGradeAccessGuard guard = mock(ClassGradeAccessGuard.class);
    private final StudentNameResolver names = mock(StudentNameResolver.class);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID enrollmentA = UUID.randomUUID();
    private final UUID enrollmentB = UUID.randomUUID();
    private final UUID studentA = UUID.randomUUID();
    private final UUID studentB = UUID.randomUUID();

    /** Đồng hồ chốt ở 2026-01-21 23:00 giờ trung tâm - mọi con số dưới đây tính tới mốc đó. */
    private GetClassProgress useCaseAt(Instant now) {
        return new GetClassProgress(enrollments, guard, names, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void stubClass(Integer standardSessionCount, List<WeeklyScheduleSlot> schedule) {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenReturn(new CoursesManagement.ClassInfoResponse(classId, courseId, UUID.randomUUID(),
                        actorAccountId, 20, true, standardSessionCount, schedule));
    }

    private void stubRoster(List<EnrollmentManagement.ClassRosterEntry> roster) {
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(roster);
        when(names.namesOf(roster.stream()
                .map(EnrollmentManagement.ClassRosterEntry::studentProfileId).toList()))
                .thenReturn(Map.of(studentA, "Nguyễn Văn A", studentB, "Trần Thị B"));
    }

    private static EnrollmentManagement.ClassRosterEntry active(UUID enrollmentId, UUID studentProfileId,
            UUID courseId, Instant enrolledAt) {
        return new EnrollmentManagement.ClassRosterEntry(enrollmentId, studentProfileId, courseId,
                EnrollmentConstants.EnrollmentStatus.ACTIVE, enrolledAt, null);
    }

    private StudentProgressRow rowOf(UUID enrollmentId, List<StudentProgressRow> rows) {
        return rows.stream().filter(row -> row.enrollmentId().equals(enrollmentId)).findFirst()
                .orElseThrow();
    }

    /**
     * Review Focus #1: hai học viên CÙNG lớp, cùng {@code Class}/{@code Course}, {@code enrolledAt}
     * khác nhau PHẢI ra % khác nhau. Lớp 24 buổi, lịch T2+T4: A vào 05/01 học 6 buổi (25%),
     * B vào 14/01 học 3 buổi (13%).
     */
    @Test
    void givesTwoDifferentPercentsToTwoStudentsInTheSameClass() {
        stubClass(24, MON_WED_EVENING);
        stubRoster(List.of(
                active(enrollmentA, studentA, courseId, at(2026, 1, 5, 8, 0)),
                active(enrollmentB, studentB, courseId, at(2026, 1, 14, 8, 0))));

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        var rowA = rowOf(enrollmentA, progress.rows());
        var rowB = rowOf(enrollmentB, progress.rows());
        assertThat(rowA.elapsedSessions()).isEqualTo(6);
        assertThat(rowA.progressPercent()).as("6/24 = 25%").isEqualTo(25);
        assertThat(rowB.elapsedSessions()).isEqualTo(3);
        assertThat(rowB.progressPercent()).as("3/24 = 12.5 → 13%").isEqualTo(13);
        assertThat(rowA.progressPercent()).isNotEqualTo(rowB.progressPercent());
        assertThat(rowA.studentName()).isEqualTo("Nguyễn Văn A");
    }

    /**
     * Review Focus #2: ghi danh WITHDRAWN đóng băng tại {@code withdrawnAt}. Cùng một dữ liệu, đọc
     * ở hai thời điểm cách nhau ba tháng phải ra CÙNG con số - nếu usecase dùng "bây giờ" làm mốc
     * kết thúc cho người đã rút thì khẳng định thứ hai sẽ đỏ.
     */
    @Test
    void freezesAWithdrawnEnrolmentAtItsWithdrawnAt() {
        stubClass(24, MON_WED_EVENING);
        stubRoster(List.of(new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                EnrollmentConstants.EnrollmentStatus.WITHDRAWN, at(2026, 1, 5, 8, 0),
                at(2026, 1, 13, 9, 0))));

        var readSoon = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);
        var readMuchLater = useCaseAt(at(2026, 4, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(rowOf(enrollmentA, readSoon.rows()).elapsedSessions())
                .as("05,07,12 tháng 1 = 3 buổi tính tới mốc rút 13/01")
                .isEqualTo(3);
        assertThat(rowOf(enrollmentA, readMuchLater.rows()).elapsedSessions())
                .as("ba tháng sau vẫn đúng 3 buổi - tiến trình đã đóng băng")
                .isEqualTo(3);
        assertThat(rowOf(enrollmentA, readMuchLater.rows()).progressPercent())
                .isEqualTo(rowOf(enrollmentA, readSoon.rows()).progressPercent());
        assertThat(rowOf(enrollmentA, readMuchLater.rows()).withdrawnAt()).isNotNull();
    }

    /** COMPLETED không cần mốc riêng: công thức có trần {@code min(...)} nên tự dừng ở 100%
     * (spec mục 5). */
    @Test
    void capsACompletedEnrolmentAtOneHundredPercentWithoutASpecialMark() {
        stubClass(4, MON_WED_EVENING);
        stubRoster(List.of(new EnrollmentManagement.ClassRosterEntry(enrollmentA, studentA, courseId,
                EnrollmentConstants.EnrollmentStatus.COMPLETED, at(2026, 1, 5, 8, 0), null)));

        var progress = useCaseAt(at(2026, 3, 1, 23, 0)).execute(actorAccountId, classId);

        assertThat(rowOf(enrollmentA, progress.rows()).progressPercent()).isEqualTo(100);
    }

    /**
     * Review Focus #8: {@code standardSessionCount} chưa cấu hình → {@code progressPercent} null ở
     * cả mức lớp và từng dòng, KHÔNG chia cho 0 và không trả một con số sai. {@code elapsedSessions}
     * vẫn có số thật để người xem hiểu null đến từ thiếu cấu hình, không phải thiếu dữ liệu học.
     */
    @Test
    void reportsNotConfiguredWhenTheCourseHasNoStandardSessionCount() {
        stubClass(null, MON_WED_EVENING);
        stubRoster(List.of(active(enrollmentA, studentA, courseId, at(2026, 1, 5, 8, 0))));

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(progress.standardSessionCount()).isNull();
        var rowA = rowOf(enrollmentA, progress.rows());
        assertThat(rowA.progressPercent()).isNull();
        assertThat(rowA.standardSessionCount()).isNull();
        assertThat(rowA.elapsedSessions()).isEqualTo(6);
    }

    /** Review Focus #12: lớp chưa có lịch học nào → 0 buổi, 0%, không ném. */
    @Test
    void reportsZeroForAClassWithoutAnySchedule() {
        stubClass(24, List.of());
        stubRoster(List.of(active(enrollmentA, studentA, courseId, at(2026, 1, 5, 8, 0))));

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(rowOf(enrollmentA, progress.rows()).elapsedSessions()).isZero();
        assertThat(rowOf(enrollmentA, progress.rows()).progressPercent()).isZero();
    }

    @Test
    void returnsAnEmptyRowListForAClassWithoutAnyEnrolment() {
        stubClass(24, MON_WED_EVENING);
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(List.of());
        when(names.namesOf(List.of())).thenReturn(Map.of());

        var progress = useCaseAt(at(2026, 1, 21, 23, 0)).execute(actorAccountId, classId);

        assertThat(progress.rows()).isEmpty();
        assertThat(progress.classId()).isEqualTo(classId);
        assertThat(progress.standardSessionCount()).isEqualTo(24);
    }

    /** Review Focus #4: đường đọc tiến trình cũng đi qua guard. */
    @Test
    void refusesACallerTheGuardRejects() {
        when(guard.requireAccessToClass(actorAccountId, classId, AccessConstants.Actions.READ))
                .thenThrow(new NotAssignedToClassException(actorAccountId, classId));

        assertThatThrownBy(() -> useCaseAt(Instant.now()).execute(actorAccountId, classId))
                .isInstanceOf(NotAssignedToClassException.class);
    }
}
