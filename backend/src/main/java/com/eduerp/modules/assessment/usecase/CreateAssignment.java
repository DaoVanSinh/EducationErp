package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.AssessmentEvents;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.AssignmentRequests;
import com.eduerp.modules.assessment.dto.CreatedResponse;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.model.Assignment;
import com.eduerp.modules.assessment.internal.repository.AssignmentRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Giáo viên được phân công (hoặc admin) giao một bài tập / mini-test cho lớp. */
@Service
public class CreateAssignment {

    private final AssignmentRepository assignments;
    private final ClassAssessmentAccessGuard guard;
    private final ApplicationEventPublisher events;

    CreateAssignment(AssignmentRepository assignments, ClassAssessmentAccessGuard guard,
            ApplicationEventPublisher events) {
        this.assignments = assignments;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public CreatedResponse execute(UUID actorAccountId, UUID actorBranchId,
            AssignmentRequests.CreateAssignmentRequest request) {
        var classInfo = guard.requireAccessToClass(actorAccountId, request.classId(), AccessConstants.Actions.CREATE);
        // Giao bài khi lớp còn mở (kể cả trước khai giảng, để giáo viên chuẩn bị); đóng lớp thì thôi.
        if (!classInfo.acceptsEnrollment()) {
            throw new AssessmentException.ClassClosed(request.classId());
        }
        var saved = assignments.save(new Assignment(request.classId(), request.title().strip(),
                request.description(), request.type(), request.dueDate(), request.maxScore(), actorAccountId));
        events.publishEvent(new AssessmentEvents.AssignmentCreated(saved.getId(), request.classId(),
                actorAccountId, actorBranchId));
        return new CreatedResponse(saved.getId());
    }
}
