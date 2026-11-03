package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.assessment.AssessmentConstants;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.internal.access.StudentEnrollmentLocator;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bước học viên trong luồng bài tập: giáo viên giao bài, học viên nộp, giáo viên chấm, học viên xem điểm.
 * Được nộp lại đến khi giáo viên chấm, vì điểm phải gắn với đúng nội dung đã chấm. Quá hạn vẫn nhận bài nhưng
 * ghi là nộp muộn. {@code enrollmentId} luôn suy từ phiên đăng nhập, không nhận từ client.
 */
@Service
public class SubmitAssignment {

    private final AssignmentRepository assignments;
    private final StudentEnrollmentLocator locator;
    private final Clock clock;

    @Autowired
    SubmitAssignment(AssignmentRepository assignments, StudentEnrollmentLocator locator) {
        this(assignments, locator, Clock.system(ZoneId.of(AssessmentConstants.Deadlines.CENTER_ZONE_ID)));
    }

    /** Constructor cho test: đúng hạn hay muộn phụ thuộc "hôm nay" nên phải chốt được Clock. */
    SubmitAssignment(AssignmentRepository assignments, StudentEnrollmentLocator locator, Clock clock) {
        this.assignments = assignments;
        this.locator = locator;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID accountId, UUID assignmentId, AssignmentRequests.SubmitAssignmentRequest request) {
        var assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new AssessmentException.AssignmentNotFound(assignmentId));
        var enrollmentId = locator.requireActiveEnrollment(accountId, assignment.getClassId());
        if (!assignment.isActive()) {
            throw new AssessmentException.AssignmentInactive(assignmentId);
        }
        var graded = assignment.resultOf(enrollmentId).filter(result -> result.getScore() != null).isPresent();
        if (graded) {
            throw new AssessmentException.AssignmentAlreadyGraded(assignmentId);
        }

        var status = AssessmentRules.submissionStatusOn(assignment.getDueDate(), LocalDate.now(clock));
        assignment.recordSubmission(enrollmentId, request.content().strip(), status, accountId);
        try {
            // Flush ngay trong khối try: để tới lúc commit thì lỗi UNIQUE nằm ngoài catch và rơi thành 500.
            assignments.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException raceLostToAnotherRequest) {
            throw new AssessmentException.SubmissionConflict(assignmentId);
        }
    }
}
