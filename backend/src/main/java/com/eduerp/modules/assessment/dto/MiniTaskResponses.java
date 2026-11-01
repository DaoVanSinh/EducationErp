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

    /**
     * {@code correctOptionIndex} và {@code explanation} null khi trả cho học viên đang làm bài - không lộ đáp
     * án trước khi nộp.
     */
    public record QuestionResponse(UUID id, String content, List<String> options, Integer correctOptionIndex,
            String explanation) {
    }

    /**
     * Một mini-task của lớp kèm thống kê cho giáo viên: tổng số lượt làm, số học viên đã làm, số học viên đã
     * ĐẠT ngưỡng {@code passPercent}, trung bình điểm CAO NHẤT của mỗi học viên (null khi chưa ai làm).
     */
    public record MiniTaskResponse(UUID id, UUID classId, String title, String description, boolean active,
            int passPercent, Instant createdAt, List<QuestionResponse> questions, int attemptCount,
            int studentsAttempted, int studentsPassed, Integer averageBestScorePercent) {
    }

    /** Một học viên × mọi mini-task của lớp: điểm cao nhất và số lượt làm. Khuyết khoá = chưa làm. */
    public record StudentMiniTaskRow(UUID enrollmentId, UUID studentProfileId, String studentName,
            EnrollmentConstants.EnrollmentStatus enrollmentStatus, Map<UUID, Integer> bestScoreByMiniTaskId,
            Map<UUID, Integer> attemptsByMiniTaskId) {
    }

    public record ClassMiniTasksResponse(UUID classId, List<MiniTaskResponse> miniTasks,
            List<StudentMiniTaskRow> rows) {
    }

    /** Đề mini-task cho học viên làm - không có đáp án, có ngưỡng đạt để học viên biết mục tiêu. */
    public record PracticeMiniTaskResponse(UUID id, UUID classId, String title, String description,
            int passPercent, List<QuestionResponse> questions) {
    }

    public record QuestionResult(UUID questionId, Integer selectedOptionIndex, int correctOptionIndex,
            boolean correct, String explanation) {
    }

    /** Kết quả chấm ngay sau khi nộp, kèm đáp án đúng + giải thích từng câu để học viên tự xem lại. */
    public record AttemptResultResponse(UUID attemptId, int correctCount, int totalQuestions, int scorePercent,
            int passPercent, boolean passed, Instant submittedAt, List<QuestionResult> results) {
    }
}
