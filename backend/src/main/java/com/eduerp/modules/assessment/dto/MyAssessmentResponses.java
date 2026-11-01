package com.eduerp.modules.assessment.dto;

import com.eduerp.modules.assessment.AssessmentConstants;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Cổng học tập: bài tập/mini-test và mini-task của CHÍNH học viên đang đăng nhập. */
public final class MyAssessmentResponses {

    private MyAssessmentResponses() {
    }

    /** Chưa có kết quả nào được ghi nhận thì {@code status} là NOT_SUBMITTED và {@code score} null. */
    public record MyAssignmentItem(UUID assignmentId, String title, String description,
            AssessmentConstants.AssignmentType type, LocalDate dueDate, BigDecimal maxScore,
            AssessmentConstants.SubmissionStatus status, BigDecimal score, String feedback) {
    }

    /** {@code passed} = điểm cao nhất đã chạm {@code passPercent} của mini-task. */
    public record MyMiniTaskItem(UUID miniTaskId, String title, String description, int questionCount,
            int attemptCount, Integer bestScorePercent, Integer lastScorePercent, int passPercent, boolean passed) {
    }

    /** Một lớp đã ghi danh. Chỉ liệt kê bài/mini-task đang bật. {@code completionPercent} null khi lớp chưa
     * có bài nào. */
    public record MyAssessmentClassResponse(UUID enrollmentId, UUID classId, String classCode, String courseName,
            EnrollmentConstants.EnrollmentStatus enrollmentStatus, List<MyAssignmentItem> assignments,
            List<MyMiniTaskItem> miniTasks, int submittedCount, Integer completionPercent) {
    }
}
