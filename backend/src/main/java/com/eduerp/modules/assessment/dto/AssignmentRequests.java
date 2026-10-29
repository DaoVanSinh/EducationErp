package com.eduerp.modules.assessment.dto;

import com.eduerp.modules.assessment.AssessmentConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Các request của phần bài tập / mini-test, gom một chỗ vì đi cùng nhau. */
public final class AssignmentRequests {

    private AssignmentRequests() {
    }

    public record CreateAssignmentRequest(@NotNull UUID classId,
            @NotBlank @Size(max = AssessmentConstants.Limits.MAX_TITLE_LENGTH) String title,
            @Size(max = AssessmentConstants.Limits.MAX_DESCRIPTION_LENGTH) String description,
            @NotNull AssessmentConstants.AssignmentType type, LocalDate dueDate,
            @NotNull @Positive @DecimalMax(AssessmentConstants.Limits.MAX_SCORE_CEILING) BigDecimal maxScore) {
    }

    /** Thay toàn bộ thông tin bài; {@code active = false} là "xoá" mềm (hệ thống không dùng DELETE). */
    public record UpdateAssignmentRequest(
            @NotBlank @Size(max = AssessmentConstants.Limits.MAX_TITLE_LENGTH) String title,
            @Size(max = AssessmentConstants.Limits.MAX_DESCRIPTION_LENGTH) String description,
            @NotNull AssessmentConstants.AssignmentType type, LocalDate dueDate,
            @NotNull @Positive @DecimalMax(AssessmentConstants.Limits.MAX_SCORE_CEILING) BigDecimal maxScore,
            @NotNull Boolean active) {
    }

    /** Kết quả của một học viên. {@code score} chỉ được có khi đã nộp; trần là {@code maxScore} của bài. */
    public record ResultEntryRequest(@NotNull UUID enrollmentId,
            @NotNull AssessmentConstants.SubmissionStatus status, @PositiveOrZero BigDecimal score,
            @Size(max = AssessmentConstants.Limits.MAX_FEEDBACK_LENGTH) String feedback) {
    }

    public record RecordResultsRequest(@NotEmpty List<@Valid @NotNull ResultEntryRequest> entries) {
    }
}
