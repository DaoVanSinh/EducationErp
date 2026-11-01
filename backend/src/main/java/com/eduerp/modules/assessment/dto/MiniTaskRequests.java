package com.eduerp.modules.assessment.dto;

import com.eduerp.modules.assessment.AssessmentConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Các request của phần mini-task. */
public final class MiniTaskRequests {

    private MiniTaskRequests() {
    }

    /**
     * Một câu trắc nghiệm: 2-6 phương án, {@code correctOptionIndex} tính từ 0. {@code explanation} (tuỳ chọn)
     * là lời giải học viên thấy sau khi nộp.
     */
    public record QuestionRequest(
            @NotBlank @Size(max = AssessmentConstants.Limits.MAX_QUESTION_LENGTH) String content,
            @NotNull @Size(min = AssessmentConstants.Limits.MIN_OPTIONS, max = AssessmentConstants.Limits.MAX_OPTIONS)
            List<@NotBlank @Size(max = AssessmentConstants.Limits.MAX_OPTION_LENGTH) String> options,
            @NotNull @PositiveOrZero Integer correctOptionIndex,
            @Size(max = AssessmentConstants.Limits.MAX_EXPLANATION_LENGTH) String explanation) {
    }

    /** {@code passPercent} bỏ trống = mặc định {@link AssessmentConstants.Limits#DEFAULT_PASS_PERCENT}. */
    public record CreateMiniTaskRequest(@NotNull UUID classId,
            @NotBlank @Size(max = AssessmentConstants.Limits.MAX_TITLE_LENGTH) String title,
            @Size(max = AssessmentConstants.Limits.MAX_DESCRIPTION_LENGTH) String description,
            @Min(AssessmentConstants.Limits.MIN_PASS_PERCENT) @Max(AssessmentConstants.Limits.MAX_PASS_PERCENT)
            Integer passPercent,
            @NotEmpty @Size(max = AssessmentConstants.Limits.MAX_QUESTIONS) List<@Valid @NotNull QuestionRequest> questions) {
    }

    /** Sửa = thay TOÀN BỘ danh sách câu hỏi (mirror cách sửa cột điểm của khóa học). */
    public record UpdateMiniTaskRequest(
            @NotBlank @Size(max = AssessmentConstants.Limits.MAX_TITLE_LENGTH) String title,
            @Size(max = AssessmentConstants.Limits.MAX_DESCRIPTION_LENGTH) String description,
            @Min(AssessmentConstants.Limits.MIN_PASS_PERCENT) @Max(AssessmentConstants.Limits.MAX_PASS_PERCENT)
            Integer passPercent,
            @NotNull Boolean active,
            @NotEmpty @Size(max = AssessmentConstants.Limits.MAX_QUESTIONS) List<@Valid @NotNull QuestionRequest> questions) {
    }

    /** Phương án học viên chọn cho từng câu theo đúng thứ tự câu; {@code null} = bỏ trống câu đó. */
    public record SubmitAttemptRequest(@NotNull @Size(max = AssessmentConstants.Limits.MAX_QUESTIONS)
            List<Integer> answers) {
    }
}
