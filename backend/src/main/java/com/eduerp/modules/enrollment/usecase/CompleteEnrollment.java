package com.eduerp.modules.enrollment.usecase;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentEvents;
import com.eduerp.modules.enrollment.EnrollmentNotActiveException;
import com.eduerp.modules.enrollment.EnrollmentNotFoundException;
import com.eduerp.modules.enrollment.internal.repository.EnrollmentRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteEnrollment {

    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;

    CompleteEnrollment(EnrollmentRepository enrollments, ApplicationEventPublisher events) {
        this.enrollments = enrollments;
        this.events = events;
    }

    @Transactional
    public void execute(UUID enrollmentId, UUID actorAccountId, UUID actorBranchId) {
        var enrollment = enrollments.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
        if (enrollment.getStatus() != EnrollmentConstants.EnrollmentStatus.ACTIVE) {
            throw new EnrollmentNotActiveException(enrollmentId);
        }
        enrollment.complete();
        events.publishEvent(new EnrollmentEvents.EnrollmentCompleted(enrollmentId, actorAccountId, actorBranchId));
    }
}
