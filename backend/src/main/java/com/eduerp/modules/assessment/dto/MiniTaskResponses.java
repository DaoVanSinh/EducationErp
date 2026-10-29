package com.eduerp.modules.assessment.dto;

import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Các response của phần mini-task. */
public final class MiniTaskResponses {

    private MiniTaskResponses() {
    }

    /** {@code correctOptionIndex} null khi trả cho học viên đang làm bài - không lộ đáp án trước khi nộp. */
    public record QuestionResponse(UUID id, String content, List<String> options, Integer correctOptionIndex) {
    }

    /**
     * Một mini-task của lớp kèm thống kê cho giáo viên: tổng số lượt làm, số học viên đã làm, trung bình
     * điểm CAO NHẤT của mỗi học viên (null khi chưa ai làm).
     */
    public record MiniTaskResponse(UUID id, UUID classId, String title, String description, boolean active,
            Instant createdAt, List<QuestionResponse> questions, int attemptCount, int studentsAttempted,
            Integer averageBestScorePercent) {
    }

    /** Một học viên × mọi mini-task của lớp: điểm cao nhất và số lượt làm. Khuyết khoá = chưa làm. */
    public record StudentMiniTaskRow(UUID enrollmentId, UUID studentProfileId, String studentName,
            EnrollmentConstants.EnrollmentStatus enrollmentStatus, Map<UUID, Integer> bestScoreByMiniTaskId,
            Map<UUID, Integer> attemptsByMiniTaskId) {
    }

    public record ClassMiniTasksResponse(UUID classId, List<MiniTaskResponse> miniTasks,
            List<StudentMiniTaskRow> rows) {
    }

    /** Đề mini-task cho học viên làm - không có đáp án. */
    public record PracticeMiniTaskResponse(UUID id, UUID classId, String title, String description,
            List<QuestionResponse> questions) {
    }

    public record QuestionResult(UUID questionId, Integer selectedOptionIndex, int correctOptionIndex,
            boolean correct) {
    }

    /** Kết quả chấm ngay sau khi nộp, kèm đáp án đúng từng câu để học viên tự xem lại. */
    public record AttemptResultResponse(UUID attemptId, int correctCount, int totalQuestions, int scorePercent,
            Instant submittedAt, List<QuestionResult> results) {
    }
}
