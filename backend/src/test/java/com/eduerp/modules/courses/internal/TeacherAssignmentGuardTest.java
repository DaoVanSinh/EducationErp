package com.eduerp.modules.courses.internal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.TeacherEligibilityPort;
import com.eduerp.modules.courses.dto.WeeklyScheduleSlot;
import com.eduerp.modules.courses.internal.model.Class;
import com.eduerp.modules.courses.internal.repository.ClassRepository;
import com.eduerp.modules.identity.IdentityManagement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * "Là giáo viên" = có năng lực giảng dạy ({@code TEACHING:READ}), không phải mang đúng mã vai trò. Phân công còn bị chặn
 * khi đã nghỉ, chưa có lương, khác chi nhánh chủ quản hoặc trùng giờ.
 */
class TeacherAssignmentGuardTest {

    private static final LocalDate START = LocalDate.of(2026, 12, 7);
    private static final List<WeeklyScheduleSlot> MON_EVENING = List.of(new WeeklyScheduleSlot(
            CoursesConstants.DayOfWeek.MON, LocalTime.of(18, 0), LocalTime.of(20, 0)));

    private final IdentityManagement identity = mock(IdentityManagement.class);
    private final AccessManagement access = mock(AccessManagement.class);
    private final TeacherEligibilityPort eligibility = mock(TeacherEligibilityPort.class);
    private final ClassRepository classes = mock(ClassRepository.class);
    private final TeacherAssignmentGuard guard = new TeacherAssignmentGuard(identity, access, eligibility, classes);
    private final UUID accountId = UUID.randomUUID();
    private final UUID branchId = UUID.randomUUID();
    private final TeacherAssignmentGuard.ClassPlan plan = new TeacherAssignmentGuard.ClassPlan(branchId, MON_EVENING,
            START, START.plusMonths(3), null);

    @BeforeEach
    void stubAssignableTeacher() {
        when(identity.summariesOf(anyCollection())).thenReturn(Map.of(accountId,
                new IdentityManagement.AccountBasicInfo(accountId, "a@eduerp.local", "A")));
        when(access.grants(accountId, AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ))
                .thenReturn(true);
        when(eligibility.assignmentProblem(accountId)).thenReturn(Optional.empty());
        when(identity.homeBranchOf(accountId)).thenReturn(Optional.of(branchId));
        when(classes.findAllByTeacherIdAndStatusIn(eq(accountId), any())).thenReturn(List.of());
    }

    private Class otherClass(UUID id, LocalTime from, LocalTime to) {
        var other = mock(Class.class);
        when(other.getId()).thenReturn(id);
        when(other.getCode()).thenReturn("K-KHAC");
        when(other.getStartDate()).thenReturn(START);
        when(other.getEndDate()).thenReturn(START.plusMonths(3));
        var slot = mock(com.eduerp.modules.courses.internal.model.ClassSchedule.class);
        when(slot.getDayOfWeek()).thenReturn(CoursesConstants.DayOfWeek.MON);
        when(slot.getStartTime()).thenReturn(from);
        when(slot.getEndTime()).thenReturn(to);
        when(other.getSchedule()).thenReturn(List.of(slot));
        return other;
    }

    @Test
    void acceptsAnEligibleTeacherOfTheSameBranchWithAFreeSlot() {
        assertThatCode(() -> guard.requireAssignable(accountId, plan)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnAccountThatDoesNotExist() {
        when(identity.summariesOf(anyCollection())).thenReturn(Map.of());

        assertThatThrownBy(() -> guard.requireAssignable(accountId, plan)).isInstanceOf(AppValidationException.class);
    }

    @Test
    void rejectsAnAccountWithoutTheTeachingCapability() {
        when(access.grants(accountId, AccessConstants.Resources.TEACHING, AccessConstants.Actions.READ))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.requireAssignable(accountId, plan)).isInstanceOf(AppValidationException.class);
    }

    /** Đã nghỉ hoặc chưa có lương: lý do do module teachers trả về qua cổng, courses không cần biết chi tiết. */
    @Test
    void rejectsATeacherWithAnEligibilityProblem() {
        when(eligibility.assignmentProblem(accountId)).thenReturn(Optional.of("Giáo viên chưa có lương"));

        assertThatThrownBy(() -> guard.requireAssignable(accountId, plan))
                .isInstanceOf(AppValidationException.class).hasMessageContaining("chưa có lương");
    }

    @Test
    void rejectsATeacherFromAnotherBranch() {
        when(identity.homeBranchOf(accountId)).thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> guard.requireAssignable(accountId, plan)).isInstanceOf(AppValidationException.class);
    }

    @Test
    void rejectsATeacherWithoutAHomeBranch() {
        when(identity.homeBranchOf(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAssignable(accountId, plan)).isInstanceOf(AppValidationException.class);
    }

    @Test
    void rejectsAClashWithAnotherOpenClass() {
        var clashing = otherClass(UUID.randomUUID(), LocalTime.of(19, 0), LocalTime.of(21, 0));
        when(classes.findAllByTeacherIdAndStatusIn(eq(accountId), any())).thenReturn(List.of(clashing));

        assertThatThrownBy(() -> guard.requireAssignable(accountId, plan))
                .isInstanceOf(AppValidationException.class).hasMessageContaining("K-KHAC");
    }

    @Test
    void acceptsBackToBackClassesAndIgnoresTheClassBeingEdited() {
        var next = otherClass(UUID.randomUUID(), LocalTime.of(20, 0), LocalTime.of(22, 0));
        var editedId = UUID.randomUUID();
        var edited = otherClass(editedId, LocalTime.of(18, 0), LocalTime.of(20, 0));
        when(classes.findAllByTeacherIdAndStatusIn(eq(accountId), any())).thenReturn(List.of(next, edited));

        assertThatCode(() -> guard.requireAssignable(accountId, new TeacherAssignmentGuard.ClassPlan(branchId,
                MON_EVENING, START, START.plusMonths(3), editedId))).doesNotThrowAnyException();
    }
}
