package com.eduerp.modules.assessment.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eduerp.modules.assessment.AssessmentConstants.AssignmentType;
import com.eduerp.modules.assessment.AssessmentConstants.SubmissionStatus;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.internal.access.StudentEnrollmentLocator;
import com.eduerp.modules.assessment.internal.model.Assignment;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class SubmitAssignmentTest {

    private static final ZoneId CENTER = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 3);
    /** 23:30 ngày 03/11 giờ Việt Nam: giờ UTC vẫn cùng ngày nhưng sát ranh giới của hạn nộp. */
    private static final Instant LAST_MINUTE = Instant.parse("2026-11-03T16:30:00Z");
    /** 00:30 ngày 04/11 giờ Việt Nam, trong khi giờ UTC vẫn là ngày 03/11. */
    private static final Instant JUST_AFTER_DUE = Instant.parse("2026-11-03T17:30:00Z");

    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final StudentEnrollmentLocator locator = mock(StudentEnrollmentLocator.class);

    private final UUID studentAccount = UUID.randomUUID();
    private final UUID teacherAccount = UUID.randomUUID();
    private final UUID classId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();

    private SubmitAssignment useCaseAt(Instant now) {
        return new SubmitAssignment(assignments, locator, Clock.fixed(now, CENTER));
    }

    private Assignment stubAssignment() {
        var assignment = new Assignment(classId, "Bài 1", null, AssignmentType.HOMEWORK, DUE_DATE,
                new BigDecimal("10.00"), teacherAccount);
        when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(locator.requireActiveEnrollment(studentAccount, classId)).thenReturn(enrollmentId);
        return assignment;
    }

    private static AssignmentRequests.SubmitAssignmentRequest answer(String content) {
        return new AssignmentRequests.SubmitAssignmentRequest(content);
    }

    @Test
    void submittingOnTheDueDateCountsAsOnTimeAndStoresTheContent() {
        var assignment = stubAssignment();

        useCaseAt(LAST_MINUTE).execute(studentAccount, assignmentId, answer("  Bài làm của em  "));

        var result = assignment.resultOf(enrollmentId).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
        assertThat(result.getSubmissionContent()).isEqualTo("Bài làm của em");
        assertThat(result.getSubmittedAt()).isNotNull();
        assertThat(result.getScore()).isNull();
    }

    @Test
    void lateIsDecidedByTheCenterDateNotTheUtcDate() {
        var assignment = stubAssignment();

        useCaseAt(JUST_AFTER_DUE).execute(studentAccount, assignmentId, answer("Bài muộn"));

        assertThat(assignment.resultOf(enrollmentId).orElseThrow().getStatus()).isEqualTo(SubmissionStatus.LATE);
    }

    @Test
    void submittingAgainBeforeGradingReplacesTheContentOnTheSameRow() {
        var assignment = stubAssignment();
        var useCase = useCaseAt(LAST_MINUTE);

        useCase.execute(studentAccount, assignmentId, answer("Lần một"));
        useCase.execute(studentAccount, assignmentId, answer("Lần hai"));

        assertThat(assignment.getResults()).hasSize(1);
        assertThat(assignment.getResults().get(0).getSubmissionContent()).isEqualTo("Lần hai");
    }

    @Test
    void submittingKeepsTheFeedbackTheTeacherAlreadyLeft() {
        var assignment = stubAssignment();
        assignment.recordResult(enrollmentId, SubmissionStatus.NOT_SUBMITTED, null, "Nhớ nộp nhé", teacherAccount);

        useCaseAt(LAST_MINUTE).execute(studentAccount, assignmentId, answer("Bài làm"));

        var result = assignment.resultOf(enrollmentId).orElseThrow();
        assertThat(result.getFeedback()).isEqualTo("Nhớ nộp nhé");
        assertThat(result.getStatus()).isEqualTo(SubmissionStatus.SUBMITTED);
    }

    @Test
    void aGradedAssignmentCannotBeSubmittedAgain() {
        var assignment = stubAssignment();
        assignment.recordResult(enrollmentId, SubmissionStatus.SUBMITTED, new BigDecimal("8"), null, teacherAccount);

        assertThatThrownBy(() -> useCaseAt(LAST_MINUTE).execute(studentAccount, assignmentId, answer("Sửa bài")))
                .isInstanceOf(AssessmentException.AssignmentAlreadyGraded.class);
    }

    @Test
    void anInactiveAssignmentCannotBeSubmitted() {
        var assignment = stubAssignment();
        assignment.update("Bài 1", null, AssignmentType.HOMEWORK, DUE_DATE, new BigDecimal("10.00"), false);

        assertThatThrownBy(() -> useCaseAt(LAST_MINUTE).execute(studentAccount, assignmentId, answer("Bài làm")))
                .isInstanceOf(AssessmentException.AssignmentInactive.class);
    }

    @Test
    void anUnknownAssignmentIsRejected() {
        when(assignments.findById(assignmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCaseAt(LAST_MINUTE).execute(studentAccount, assignmentId, answer("Bài làm")))
                .isInstanceOf(AssessmentException.AssignmentNotFound.class);
    }

    @Test
    void aStudentWithoutAnActiveEnrollmentIsRejectedBeforeAnythingIsSaved() {
        stubAssignment();
        when(locator.requireActiveEnrollment(studentAccount, classId))
                .thenThrow(new AssessmentException.NotEnrolledInClass(classId));

        assertThatThrownBy(() -> useCaseAt(LAST_MINUTE).execute(studentAccount, assignmentId, answer("Bài làm")))
                .isInstanceOf(AssessmentException.NotEnrolledInClass.class);
    }

    @Test
    void losingTheRaceOnTheUniqueRowBecomesAConflictInsteadOfAServerError() {
        stubAssignment();
        when(assignments.saveAndFlush(any(Assignment.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> useCaseAt(LAST_MINUTE).execute(studentAccount, assignmentId, answer("Bài làm")))
                .isInstanceOf(AssessmentException.SubmissionConflict.class);
        verify(assignments).saveAndFlush(any(Assignment.class));
    }
}
