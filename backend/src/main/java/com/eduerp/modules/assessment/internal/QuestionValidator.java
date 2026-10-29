package com.eduerp.modules.assessment.internal;

import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.MiniTaskRequests;
import com.eduerp.modules.assessment.internal.model.MiniTask;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import java.util.List;

/** Kiểm rồi nạp danh sách câu hỏi vào mini-task - dùng chung cho tạo mới và sửa. */
public final class QuestionValidator {

    private QuestionValidator() {
    }

    /** Kiểm TOÀN BỘ trước, chỉ nạp khi mọi câu hợp lệ - lỗi báo đúng số thứ tự câu (đếm từ 1). */
    public static void applyQuestions(MiniTask task, List<MiniTaskRequests.QuestionRequest> questions) {
        for (var i = 0; i < questions.size(); i++) {
            var question = questions.get(i);
            if (!AssessmentRules.isValidQuestion(question.options().size(), question.correctOptionIndex())) {
                throw new AssessmentException.InvalidQuestion(i + 1);
            }
        }
        task.clearQuestions();
        questions.forEach(question -> task.addQuestion(question.content().strip(),
                question.options().stream().map(String::strip).toList(), question.correctOptionIndex()));
    }
}
