package com.eduerp.modules.academic.internal.rules;

import com.eduerp.modules.academic.AcademicConstants;
import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}. */
public final class GradeRules {

    private GradeRules() {
    }

    /**
     * Điểm tổng kết theo spec mục 6: {@code Σ (score / maxScore * weightPercent)} qua mọi cột, CHỈ
     * KHI đủ điểm ở MỌI cột đã cấu hình cho khóa đó. Thang kết quả là 0-100 vì Σ {@code weightPercent}
     * bắt buộc bằng 100 ({@code GradeColumnRules} chặn ở phía courses).
     *
     * <p>Trả {@code null} khi thiếu điểm ở bất kỳ cột nào, hoặc khi khóa chưa cấu hình cột nào.
     * KHÔNG tính trung bình trên phần đã có: "tránh một con số gây hiểu lầm" (spec mục 6, Review
     * Focus #3). Điểm của cột đã bị xoá khỏi cấu hình bị bỏ qua - chỉ các cột ĐANG cấu hình được tính.
     */
    public static BigDecimal finalScore(List<GradeColumnResponse> columns,
            Map<UUID, BigDecimal> scoresByColumnId) {
        if (columns.isEmpty() || !hasEveryColumnScored(columns, scoresByColumnId)) {
            return null;
        }
        return columns.stream()
                .map(column -> weightedPart(column, scoresByColumnId.get(column.id())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(AcademicConstants.Scoring.SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /** Khoảng hợp lệ của một điểm là {@code [0, maxScore]} của ĐÚNG cột đó (Review Focus #10). */
    public static boolean isScoreWithinRange(BigDecimal score, BigDecimal maxScore) {
        return score.compareTo(AcademicConstants.Scoring.MIN_SCORE) >= 0
                && score.compareTo(maxScore) <= 0;
    }

    private static boolean hasEveryColumnScored(List<GradeColumnResponse> columns,
            Map<UUID, BigDecimal> scoresByColumnId) {
        return columns.stream().allMatch(column -> scoresByColumnId.get(column.id()) != null);
    }

    private static BigDecimal weightedPart(GradeColumnResponse column, BigDecimal score) {
        return score.divide(column.maxScore(), AcademicConstants.Scoring.WEIGHTED_MATH_SCALE,
                        RoundingMode.HALF_UP)
                .multiply(column.weightPercent());
    }
}
