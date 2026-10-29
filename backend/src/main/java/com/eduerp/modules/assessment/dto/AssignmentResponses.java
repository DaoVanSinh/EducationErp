package com.eduerp.modules.assessment.dto;

import com.eduerp.modules.assessment.AssessmentConstants;
import com.eduerp.modules.enrollment.EnrollmentConstants;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Các response của phần bài tập / mini-test theo lớp (góc nhìn giáo viên/admin). */
public final class AssignmentResponses {

    private AssignmentResponses() {
    }

    /**
     * Một bài của lớp kèm thống kê: {@code submittedCount} = số học viên đã nộp (đúng hạn hoặc muộn),
     * {@code rosterCount} = số học viên đang học, {@code averageScore} = điểm trung bình của những bài đã
     * có điểm (null khi chưa có điểm nào).
     */
    public record AssignmentResponse(UUID id, UUID classId, String title, String description,
            AssessmentConstants.AssignmentType type, LocalDate dueDate, BigDecimal maxScore, boolean active,
            Instant createdAt, int submittedCount, int rosterCount, BigDecimal averageScore) {
    }

    public record ResultCell(AssessmentConstants.SubmissionStatus status, BigDecimal score, String feedback) {
    }

    /**
     * Một học viên × mọi bài của lớp. Khuyết khoá nghĩa là CHƯA nộp. {@code completionPercent} chỉ tính
     * trên các bài đang bật; null khi lớp chưa có bài nào - không phải 0%.
     */
    public record StudentAssignmentRow(UUID enrollmentId, UUID studentProfileId, String studentName,
            EnrollmentConstants.EnrollmentStatus enrollmentStatus, Map<UUID, ResultCell> resultsByAssignmentId,
            int submittedCount, Integer completionPercent) {
    }

    public record ClassAssignmentsResponse(UUID classId, List<AssignmentResponse> assignments,
            List<StudentAssignmentRow> rows) {
    }
}
