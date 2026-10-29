package com.eduerp.modules.assessment.internal.rules;

import com.eduerp.modules.assessment.AssessmentConstants;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Quyết định thuần của bài tập / mini-test và mini-task - không I/O. */
public final class AssessmentRules {

    private AssessmentRules() {
    }

    /** Nộp đúng hạn hoặc nộp muộn đều tính là đã hoàn thành bài. */
    public static boolean isSubmitted(AssessmentConstants.SubmissionStatus status) {
        return status == AssessmentConstants.SubmissionStatus.SUBMITTED
                || status == AssessmentConstants.SubmissionStatus.LATE;
    }

    /** Điểm hợp lệ nằm trong {@code [0, maxScore]} của ĐÚNG bài đó. */
    public static boolean isScoreWithinRange(BigDecimal score, BigDecimal maxScore) {
        return score.compareTo(AssessmentConstants.Scoring.MIN_SCORE) >= 0 && score.compareTo(maxScore) <= 0;
    }

    /** Chưa nộp thì không thể có điểm - tránh một con số "0 điểm" bị hiểu nhầm là đã chấm. */
    public static boolean allowsScore(AssessmentConstants.SubmissionStatus status, BigDecimal score) {
        return score == null || isSubmitted(status);
    }

    /** Làm tròn {@code part / whole × 100}; mẫu số 0 trả {@code null} - không phải 0%. */
    public static Integer percent(int part, int whole) {
        if (whole == 0) {
            return null;
        }
        return BigDecimal.valueOf((long) part * AssessmentConstants.Limits.PERCENT_SCALE)
                .divide(BigDecimal.valueOf(whole), 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /** Trung bình các điểm đã có; không có điểm nào trả {@code null}. */
    public static BigDecimal averageScore(Collection<BigDecimal> scores) {
        var present = scores.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) {
            return null;
        }
        return present.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(present.size()), AssessmentConstants.Scoring.SCORE_SCALE,
                        RoundingMode.HALF_UP);
    }

    /** Một câu hợp lệ khi có 2-6 phương án và đáp án đúng là chỉ số của một phương án trong đó. */
    public static boolean isValidQuestion(int optionCount, int correctOptionIndex) {
        return optionCount >= AssessmentConstants.Limits.MIN_OPTIONS
                && optionCount <= AssessmentConstants.Limits.MAX_OPTIONS
                && correctOptionIndex >= 0 && correctOptionIndex < optionCount;
    }

    /**
     * Số câu đúng khi so phương án học viên chọn với đáp án đúng, theo thứ tự câu. Câu bỏ trống
     * ({@code null}) tính là sai. Hai danh sách phải cùng độ dài - nơi gọi kiểm trước.
     */
    public static int countCorrect(List<Integer> correctOptionIndexes, List<Integer> answers) {
        var correct = 0;
        for (var i = 0; i < correctOptionIndexes.size(); i++) {
            if (Objects.equals(correctOptionIndexes.get(i), answers.get(i))) {
                correct++;
            }
        }
        return correct;
    }
}
