package com.eduerp.modules.assessment.usecase;

import com.eduerp.modules.assessment.AssessmentException;
import com.eduerp.modules.assessment.dto.MiniTaskRequests;
import com.eduerp.modules.assessment.dto.MiniTaskResponses;
import com.eduerp.modules.assessment.internal.access.StudentEnrollmentLocator;
import com.eduerp.modules.assessment.internal.model.MiniTaskAttempt;
import com.eduerp.modules.assessment.internal.model.MiniTaskQuestion;
import com.eduerp.modules.assessment.internal.repository.MiniTaskAttemptRepository;
import com.eduerp.modules.assessment.internal.repository.MiniTaskRepository;
import com.eduerp.modules.assessment.internal.rules.AssessmentRules;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Học viên nộp một lượt làm mini-task: chấm tự động ngay, lưu lượt làm (snapshot số câu đúng), trả kết quả
 * kèm đáp án đúng từng câu để tự xem lại. Làm lại không giới hạn - đây là ôn luyện, không phải bài thi.
 */
@Service
public class SubmitMiniTaskAttempt {

    private final MiniTaskRepository miniTasks;
    private final MiniTaskAttemptRepository attempts;
    private final StudentEnrollmentLocator locator;

    SubmitMiniTaskAttempt(MiniTaskRepository miniTasks, MiniTaskAttemptRepository attempts,
            StudentEnrollmentLocator locator) {
        this.miniTasks = miniTasks;
        this.attempts = attempts;
        this.locator = locator;
    }

    @Transactional
    public MiniTaskResponses.AttemptResultResponse execute(UUID accountId, UUID miniTaskId,
            MiniTaskRequests.SubmitAttemptRequest request) {
        var task = miniTasks.findById(miniTaskId)
                .orElseThrow(() -> new AssessmentException.MiniTaskNotFound(miniTaskId));
        var enrollmentId = locator.requireEnrollment(accountId, task.getClassId());
        if (!task.isActive()) {
            throw new AssessmentException.MiniTaskInactive(miniTaskId);
        }
        var questions = task.getQuestions();
        var answers = request.answers();
        if (answers.size() != questions.size()) {
            throw new AssessmentException.AnswerCountMismatch(questions.size(), answers.size());
        }

        var correctIndexes = questions.stream().map(MiniTaskQuestion::getCorrectOptionIndex).toList();
        var correct = AssessmentRules.countCorrect(correctIndexes, answers);
        var percent = AssessmentRules.percent(correct, questions.size());
        var saved = attempts.save(new MiniTaskAttempt(miniTaskId, enrollmentId, correct, questions.size(),
                Objects.requireNonNullElse(percent, 0)));

        var results = IntStream.range(0, questions.size())
                .mapToObj(i -> new MiniTaskResponses.QuestionResult(questions.get(i).getId(), answers.get(i),
                        correctIndexes.get(i), Objects.equals(correctIndexes.get(i), answers.get(i)),
                        questions.get(i).getExplanation()))
                .toList();
        return new MiniTaskResponses.AttemptResultResponse(saved.getId(), correct, questions.size(),
                saved.getScorePercent(), task.getPassPercent(),
                AssessmentRules.isPassed(saved.getScorePercent(), task.getPassPercent()), saved.getSubmittedAt(),
                results);
    }
}
