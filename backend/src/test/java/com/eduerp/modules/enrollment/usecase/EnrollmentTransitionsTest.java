package com.eduerp.modules.enrollment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.EnrollmentNotActiveException;
import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.internal.model.Enrollment;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class EnrollmentTransitionsTest {

    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final WithdrawEnrollment withdraw = new WithdrawEnrollment(enrollments, events);
    private final CompleteEnrollment complete = new CompleteEnrollment(enrollments, events);
    private final GetEnrollment get = new GetEnrollment(enrollments);

    private final UUID actorAccountId = UUID.randomUUID();
    private final UUID actorBranchId = UUID.randomUUID();

    private Enrollment activeEnrollment() {
        return new Enrollment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                actorAccountId);
    }

    @Test
    void withdrawRejectsAnUnknownEnrollment() {
        var enrollmentId = UUID.randomUUID();
        when(enrollments.findById(enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> withdraw.execute(enrollmentId, actorAccountId, actorBranchId))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    void withdrawMovesAnActiveEnrollmentToWithdrawnAndStampsTheTime() {
        var enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        withdraw.execute(enrollment.getId(), actorAccountId, actorBranchId);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.WITHDRAWN);
        assertThat(enrollment.getWithdrawnAt()).isNotNull();
        verify(events).publishEvent(any(EnrollmentEvents.EnrollmentWithdrawn.class));
    }

    @Test
    void withdrawRefusesAnEnrollmentThatIsNoLongerActive() {
        var enrollment = activeEnrollment();
        enrollment.complete();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> withdraw.execute(enrollment.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(EnrollmentNotActiveException.class);
    }

    @Test
    void completeMovesAnActiveEnrollmentToCompletedWithoutAWithdrawalTime() {
        var enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        complete.execute(enrollment.getId(), actorAccountId, actorBranchId);

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentConstants.EnrollmentStatus.COMPLETED);
        assertThat(enrollment.getWithdrawnAt()).isNull();
        verify(events).publishEvent(any(EnrollmentEvents.EnrollmentCompleted.class));
    }

    @Test
    void completeRefusesAnEnrollmentThatIsAlreadyWithdrawn() {
        var enrollment = activeEnrollment();
        enrollment.withdraw();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        assertThatThrownBy(() -> complete.execute(enrollment.getId(), actorAccountId, actorBranchId))
                .isInstanceOf(EnrollmentNotActiveException.class);
    }

    @Test
    void getReturnsTheEnrollmentResponse() {
        var enrollment = activeEnrollment();
        when(enrollments.findById(enrollment.getId())).thenReturn(Optional.of(enrollment));

        var response = get.execute(enrollment.getId());

        assertThat(response.id()).isEqualTo(enrollment.getId());
        assertThat(response.status()).isEqualTo(EnrollmentConstants.EnrollmentStatus.ACTIVE);
    }

    @Test
    void getRejectsAnUnknownEnrollment() {
        var enrollmentId = UUID.randomUUID();
        when(enrollments.findById(enrollmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> get.execute(enrollmentId)).isInstanceOf(EnrollmentNotFoundException.class);
    }
}
