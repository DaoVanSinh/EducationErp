package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.MiniTaskRequests;
import com.eduerp.modules.assessment.internal.access.ClassAssessmentAccessGuard;
import com.eduerp.modules.assessment.internal.repository.MiniTaskRepository;
import com.eduerp.modules.assessment.internal.QuestionValidator;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sửa mini-task: thay toàn bộ câu hỏi, đổi tiêu đề, bật/tắt. Các lượt đã nộp giữ nguyên kết quả vì lưu
 * snapshot số câu đúng/tổng số câu tại lúc nộp.
 */
@Service
public class UpdateMiniTask {

    private final MiniTaskRepository miniTasks;
    private final ClassAssessmentAccessGuard guard;

    UpdateMiniTask(MiniTaskRepository miniTasks, ClassAssessmentAccessGuard guard) {
        this.miniTasks = miniTasks;
        this.guard = guard;
    }

    @Transactional
    public void execute(UUID actorAccountId, UUID miniTaskId, MiniTaskRequests.UpdateMiniTaskRequest request) {
        var task = miniTasks.findById(miniTaskId)
                .orElseThrow(() -> new AssessmentException.MiniTaskNotFound(miniTaskId));
        guard.requireAccessToClass(actorAccountId, task.getClassId(), AccessConstants.Actions.UPDATE);
        task.update(request.title().strip(), request.description(),
                AssessmentRules.passPercentOrDefault(request.passPercent()), request.active());
        QuestionValidator.applyQuestions(task, request.questions());
        miniTasks.save(task);
    }
}
