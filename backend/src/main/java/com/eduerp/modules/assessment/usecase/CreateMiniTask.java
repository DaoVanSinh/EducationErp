package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.AssessmentEvents;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.CreatedResponse;
import com.eduerp.modules.assessment.dto.MiniTaskRequests;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.model.MiniTask;
import com.eduerp.modules.assessment.internal.repository.MiniTaskRepository;
import com.eduerp.modules.assessment.internal.QuestionValidator;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Giáo viên được phân công (hoặc admin) soạn một mini-task trắc nghiệm cho lớp. */
@Service
public class CreateMiniTask {

    private final MiniTaskRepository miniTasks;
    private final ClassAssessmentAccessGuard guard;
    private final ApplicationEventPublisher events;

    CreateMiniTask(MiniTaskRepository miniTasks, ClassAssessmentAccessGuard guard, ApplicationEventPublisher events) {
        this.miniTasks = miniTasks;
        this.guard = guard;
        this.events = events;
    }

    @Transactional
    public CreatedResponse execute(UUID actorAccountId, UUID actorBranchId,
            MiniTaskRequests.CreateMiniTaskRequest request) {
        var classInfo = guard.requireAccessToClass(actorAccountId, request.classId(), AccessConstants.Actions.CREATE);
        // Giao bài khi lớp còn mở (kể cả trước khai giảng, để giáo viên chuẩn bị); đóng lớp thì thôi.
        if (!classInfo.acceptsEnrollment()) {
            throw new AssessmentException.ClassClosed(request.classId());
        }
        var task = new MiniTask(request.classId(), request.title().strip(), request.description(),
                AssessmentRules.passPercentOrDefault(request.passPercent()), actorAccountId);
        QuestionValidator.applyQuestions(task, request.questions());
        var saved = miniTasks.save(task);
        events.publishEvent(new AssessmentEvents.MiniTaskCreated(saved.getId(), request.classId(),
                actorAccountId, actorBranchId));
        return new CreatedResponse(saved.getId());
    }
}
