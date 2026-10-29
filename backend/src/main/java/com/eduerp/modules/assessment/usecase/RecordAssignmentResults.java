package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.AssessmentEvents;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.model.Assignment;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import com.eduerp.modules.enrollment.EnrollmentManagement;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nhập tình trạng nộp bài và điểm của nhiều học viên cho một bài (upsert). Kiểm TOÀN BỘ danh sách trước
 * khi ghi dòng nào: một dòng sai thì cả lần nhập bị từ chối, không ghi dở dang.
 */
@Service
public class RecordAssignmentResults {

    private final AssignmentRepository assignments;
    private final EnrollmentManagement enrollments;
    private final ClassAssessmentAccessGuard guard;
    private final ApplicationEventPublisher events;

    RecordAssignmentResults(AssignmentRepository assignments, EnrollmentManagement enrollments,
            ClassAssessmentAccessGuard guard, ApplicationEventPublisher events) {
        this.assignments = assignments;
        this.enrollments = enrollments;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public void execute(UUID actorAccountId, UUID actorBranchId, UUID assignmentId,
            AssignmentRequests.RecordResultsRequest request) {
        var assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new AssessmentException.AssignmentNotFound(assignmentId));
        guard.requireAccessToClass(actorAccountId, assignment.getClassId(), AccessConstants.Actions.UPDATE);
        validate(assignment, request);

        request.entries().forEach(entry -> assignment.recordResult(entry.enrollmentId(), entry.status(),
                entry.score(), entry.feedback(), actorAccountId));
        assignments.save(assignment);
        events.publishEvent(new AssessmentEvents.AssignmentResultsRecorded(assignmentId, actorAccountId,
                actorBranchId));
    }

    private void validate(Assignment assignment, AssignmentRequests.RecordResultsRequest request) {
        Set<UUID> roster = enrollments.listEnrollmentsByClass(assignment.getClassId()).stream()
                .map(EnrollmentManagement.ClassRosterEntry::enrollmentId)
                .collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>();
        for (var entry : request.entries()) {
            if (!seen.add(entry.enrollmentId())) {
                throw new AssessmentException.DuplicateEntry(entry.enrollmentId());
            }
            if (!roster.contains(entry.enrollmentId())) {
                throw new AssessmentException.EnrollmentNotInClass(entry.enrollmentId(), assignment.getClassId());
            }
            if (!AssessmentRules.allowsScore(entry.status(), entry.score())) {
                throw new AssessmentException.ScoreWithoutSubmission(entry.enrollmentId());
            }
            if (entry.score() != null && !AssessmentRules.isScoreWithinRange(entry.score(), assignment.getMaxScore())) {
                throw new AssessmentException.InvalidScore(entry.score(), assignment.getMaxScore());
            }
        }
    }
}
