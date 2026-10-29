package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sửa thông tin bài, hoặc tắt bài ({@code active = false}) - không xoá cứng kết quả đã nhập. */
@Service
public class UpdateAssignment {

    private final AssignmentRepository assignments;
    private final ClassAssessmentAccessGuard guard;

    UpdateAssignment(AssignmentRepository assignments, ClassAssessmentAccessGuard guard) {
        this.assignments = assignments;
        this.guard = guard;
    }

    @Transactional
    public void execute(UUID actorAccountId, UUID assignmentId, AssignmentRequests.UpdateAssignmentRequest request) {
        var assignment = assignments.findById(assignmentId)
                .orElseThrow(() -> new AssessmentException.AssignmentNotFound(assignmentId));
        guard.requireAccessToClass(actorAccountId, assignment.getClassId(), AccessConstants.Actions.UPDATE);
        assignment.update(request.title().strip(), request.description(), request.type(), request.dueDate(),
                request.maxScore(), request.active());
    }
}
