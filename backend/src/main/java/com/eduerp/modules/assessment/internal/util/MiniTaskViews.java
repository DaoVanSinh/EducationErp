package com.eduerp.modules.assessment.internal.util;

import com.eduerp.modules.assessment.dto.MiniTaskResponses;
import com.eduerp.modules.assessment.internal.model.MiniTask;
import java.util.List;

/** Biến đổi thuần MiniTask → DTO, dùng chung cho góc nhìn giáo viên (có đáp án) và học viên (không). */
public final class MiniTaskViews {

    private MiniTaskViews() {
    }

    public static List<MiniTaskResponses.QuestionResponse> questionsWithAnswers(MiniTask task) {
        return task.getQuestions().stream()
                .map(question -> new MiniTaskResponses.QuestionResponse(question.getId(), question.getContent(),
                        List.copyOf(question.getOptions()), question.getCorrectOptionIndex()))
                .toList();
    }

    /** Đề cho học viên: KHÔNG có đáp án đúng trước khi nộp. */
    public static List<MiniTaskResponses.QuestionResponse> questionsWithoutAnswers(MiniTask task) {
        return task.getQuestions().stream()
                .map(question -> new MiniTaskResponses.QuestionResponse(question.getId(), question.getContent(),
                        List.copyOf(question.getOptions()), null))
                .toList();
    }
}
