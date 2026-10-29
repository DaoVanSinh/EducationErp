package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.MiniTaskResponses;
import com.eduerp.modules.assessment.internal.access.StudentEnrollmentLocator;
import com.eduerp.modules.assessment.internal.repository.MiniTaskRepository;
import com.eduerp.modules.assessment.internal.util.MiniTaskViews;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đề mini-task cho học viên làm: chỉ học viên của lớp đó, chỉ khi mini-task đang bật, KHÔNG có đáp án. */
@Service
public class GetMyMiniTask {

    private final MiniTaskRepository miniTasks;
    private final StudentEnrollmentLocator locator;

    GetMyMiniTask(MiniTaskRepository miniTasks, StudentEnrollmentLocator locator) {
        this.miniTasks = miniTasks;
        this.locator = locator;
    }

    @Transactional(readOnly = true)
    public MiniTaskResponses.PracticeMiniTaskResponse execute(UUID accountId, UUID miniTaskId) {
        var task = miniTasks.findById(miniTaskId)
                .orElseThrow(() -> new AssessmentException.MiniTaskNotFound(miniTaskId));
        locator.requireEnrollment(accountId, task.getClassId());
        if (!task.isActive()) {
            throw new AssessmentException.MiniTaskInactive(miniTaskId);
        }
        return new MiniTaskResponses.PracticeMiniTaskResponse(task.getId(), task.getClassId(), task.getTitle(),
                task.getDescription(), MiniTaskViews.questionsWithoutAnswers(task));
    }
}
