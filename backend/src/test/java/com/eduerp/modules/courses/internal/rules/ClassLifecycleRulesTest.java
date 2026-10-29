package com.eduerp.modules.courses.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.courses.ClassLifecycleException;
import com.eduerp.modules.courses.CoursesConstants.ClassStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** "Khi nào lớp đủ người, khi nào bắt đầu học" - luật thuần, không cần Spring. */
class ClassLifecycleRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    private final UUID classId = UUID.randomUUID();

    @Test
    void startsWhenEnrollingOnOrAfterTheStartDateWithScheduleAndEnoughStudents() {
        assertThatCode(() -> ClassLifecycleRules.requireStartable(classId, ClassStatus.ENROLLING, TODAY, TODAY,
                true, 10, 10)).doesNotThrowAnyException();
        assertThat(ClassLifecycleRules.isStartable(ClassStatus.ENROLLING, TODAY.minusDays(3), TODAY, true, 12, 10))
                .isTrue();
    }

    @Test
    void doesNotStartBeforeTheStartDate() {
        assertThatThrownBy(() -> ClassLifecycleRules.requireStartable(classId, ClassStatus.ENROLLING,
                TODAY.plusDays(1), TODAY, true, 10, 10))
                .isInstanceOf(ClassLifecycleException.StartDateNotReached.class);
    }

    @Test
    void doesNotStartWithoutASchedule() {
        assertThatThrownBy(() -> ClassLifecycleRules.requireStartable(classId, ClassStatus.ENROLLING, TODAY, TODAY,
                false, 10, 10)).isInstanceOf(ClassLifecycleException.NoSchedule.class);
    }

    @Test
    void doesNotStartBelowTheMinimumHeadcount() {
        assertThatThrownBy(() -> ClassLifecycleRules.requireStartable(classId, ClassStatus.ENROLLING, TODAY, TODAY,
                true, 9, 10)).isInstanceOf(ClassLifecycleException.NotEnoughStudents.class);
        assertThat(ClassLifecycleRules.isStartable(ClassStatus.ENROLLING, TODAY, TODAY, true, 9, 10)).isFalse();
    }

    @Test
    void onlyAnEnrollingClassCanStartOrBeCancelledAndOnlyARunningClassCanComplete() {
        assertThatThrownBy(() -> ClassLifecycleRules.requireStartable(classId, ClassStatus.IN_PROGRESS, TODAY, TODAY,
                true, 10, 1)).isInstanceOf(ClassLifecycleException.InvalidTransition.class);
        assertThatThrownBy(() -> ClassLifecycleRules.requireCancellable(classId, ClassStatus.IN_PROGRESS))
                .isInstanceOf(ClassLifecycleException.InvalidTransition.class);
        assertThatThrownBy(() -> ClassLifecycleRules.requireCompletable(classId, ClassStatus.ENROLLING))
                .isInstanceOf(ClassLifecycleException.InvalidTransition.class);
        assertThatCode(() -> ClassLifecycleRules.requireCompletable(classId, ClassStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void closedClassesAreNotEditable() {
        assertThatThrownBy(() -> ClassLifecycleRules.requireEditable(classId, ClassStatus.COMPLETED))
                .isInstanceOf(ClassLifecycleException.NotEditable.class);
        assertThatThrownBy(() -> ClassLifecycleRules.requireEditable(classId, ClassStatus.CANCELLED))
                .isInstanceOf(ClassLifecycleException.NotEditable.class);
        assertThatCode(() -> ClassLifecycleRules.requireEditable(classId, ClassStatus.IN_PROGRESS))
                .doesNotThrowAnyException();
    }

    @Test
    void validatesThePlan() {
        assertThatThrownBy(() -> ClassLifecycleRules.requireValidPlan(21, 20, TODAY, null))
                .isInstanceOf(AppValidationException.class);
        assertThatThrownBy(() -> ClassLifecycleRules.requireValidPlan(5, 20, TODAY, TODAY.minusDays(1)))
                .isInstanceOf(AppValidationException.class);
        assertThatThrownBy(() -> ClassLifecycleRules.requireStartDateNotPast(TODAY.minusDays(1), TODAY))
                .isInstanceOf(AppValidationException.class);
        assertThatThrownBy(() -> ClassLifecycleRules.requireSeatsCoverEnrolled(8, 9))
                .isInstanceOf(ClassLifecycleException.SeatsBelowEnrolled.class);
        assertThatCode(() -> ClassLifecycleRules.requireValidPlan(5, 20, TODAY, TODAY.plusMonths(3)))
                .doesNotThrowAnyException();
    }
}
