package com.eduerp.modules.assessment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.assessment.AssessmentConstants.AssignmentType;
import com.eduerp.modules.assessment.AssessmentConstants.SubmissionStatus;
import com.eduerp.modules.assessment.AssessmentEvents;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.model.Assignment;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RecordAssignmentResultsTest {

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final EnrollmentManagement enrollments = mock(EnrollmentManagement.class);
    private final ClassAssessmentAccessGuard guard = mock(ClassAssessmentAccessGuard.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final RecordAssignmentResults useCase =
            new RecordAssignmentResults(assignments, enrollments, guard, events);

    private final UUID actor = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private Assignment assignment;

    @BeforeEach
    void anAssignmentOutOfTenInAClassOfOne() {
        assignment = new Assignment(classId, "Bài 1", null, AssignmentType.HOMEWORK, null,
                new BigDecimal("10.00"), actor);
        when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(enrollments.listEnrollmentsByClass(classId)).thenReturn(List.of(new EnrollmentManagement.ClassRosterEntry(
                enrollmentId, UUID.randomUUID(), UUID.randomUUID(), EnrollmentConstants.EnrollmentStatus.ACTIVE,
                Instant.now(), null)));
    }

    private AssignmentRequests.RecordResultsRequest request(SubmissionStatus status, String score) {
        return new AssignmentRequests.RecordResultsRequest(List.of(new AssignmentRequests.ResultEntryRequest(
                enrollmentId, status, score == null ? null : new BigDecimal(score), "Tốt")));
    }

    @Test
    void recordsTheResultAndPublishesTheEvent() {
        useCase.execute(actor, null, assignmentId, request(SubmissionStatus.SUBMITTED, "9"));

        assertThat(assignment.getResults()).hasSize(1);
        assertThat(assignment.getResults().get(0).getScore()).isEqualByComparingTo("9");
        verify(events).publishEvent(any(AssessmentEvents.AssignmentResultsRecorded.class));
    }

    @Test
    void recordingAgainUpdatesTheSameRow() {
        useCase.execute(actor, null, assignmentId, request(SubmissionStatus.LATE, "6"));
        useCase.execute(actor, null, assignmentId, request(SubmissionStatus.SUBMITTED, "8"));

        assertThat(assignment.getResults()).hasSize(1);
        assertThat(assignment.getResults().get(0).getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    @Test
    void rejectsAScoreAboveTheMaxOfThisAssignment() {
        assertThatThrownBy(() -> useCase.execute(actor, null, assignmentId, request(SubmissionStatus.SUBMITTED, "10.5")))
                .isInstanceOf(AssessmentException.InvalidScore.class);
        assertThat(assignment.getResults()).isEmpty();
    }

    @Test
    void rejectsAScoreForWorkThatWasNotSubmitted() {
        assertThatThrownBy(() -> useCase.execute(actor, null, assignmentId, request(SubmissionStatus.NOT_SUBMITTED, "5")))
                .isInstanceOf(AssessmentException.ScoreWithoutSubmission.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void reportsAnUnknownAssignmentAsNotFound() {
        var unknown = UUID.randomUUID();
        when(assignments.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(actor, null, unknown, request(SubmissionStatus.SUBMITTED, "5")))
                .isInstanceOf(AssessmentException.AssignmentNotFound.class);
    }
}
