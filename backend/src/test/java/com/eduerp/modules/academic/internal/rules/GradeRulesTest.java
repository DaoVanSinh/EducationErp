package com.eduerp.modules.academic.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.dto.GradeColumnResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure, không I/O. Thang điểm tổng kết là 0-100: Σ(score / maxScore * weightPercent) với Σ weight
 * = 100 (spec mục 6). */
class GradeRulesTest {

    private final UUID midtermId = UUID.randomUUID();
    private final UUID finalId = UUID.randomUUID();

    private final GradeColumnResponse midterm = new GradeColumnResponse(midtermId, "Giữa kỳ",
            new BigDecimal("40.00"), new BigDecimal("10.00"), 0);
    private final GradeColumnResponse finalExam = new GradeColumnResponse(finalId, "Cuối kỳ",
            new BigDecimal("60.00"), new BigDecimal("10.00"), 1);

    private List<GradeColumnResponse> bothColumns() {
        return List.of(midterm, finalExam);
    }

    /** Ví dụ của spec mục 2: Giữa kỳ 40% + Cuối kỳ 60%, thang 10. 8/10*40 + 9/10*60 = 32 + 54 = 86. */
    @Test
    void sumsEveryColumnWeightedByItsOwnMaxScore() {
        var score = GradeRules.finalScore(bothColumns(), Map.of(
                midtermId, new BigDecimal("8.00"), finalId, new BigDecimal("9.00")));

        assertThat(score).isEqualByComparingTo(new BigDecimal("86.00"));
    }

    /** Hai cột có thang KHÁC NHAU vẫn phải ra đúng: 4/5*40 + 9/10*60 = 32 + 54 = 86. */
    @Test
    void handlesColumnsWithDifferentMaxScores() {
        var midtermOutOfFive = new GradeColumnResponse(midtermId, "Giữa kỳ",
                new BigDecimal("40.00"), new BigDecimal("5.00"), 0);

        var score = GradeRules.finalScore(List.of(midtermOutOfFive, finalExam), Map.of(
                midtermId, new BigDecimal("4.00"), finalId, new BigDecimal("9.00")));

        assertThat(score).isEqualByComparingTo(new BigDecimal("86.00"));
    }

    /**
     * Review Focus #3: thiếu điểm ở MỘT cột → null, KHÔNG tính trung bình trên phần đã có. Nếu hàm
     * này trả 32.00 ở đây thì người xem sẽ tưởng học viên được 32/100 trong khi thực tế chỉ là chưa
     * nhập điểm cuối kỳ (spec mục 6: "tránh một con số gây hiểu lầm").
     */
    @Test
    void returnsNullWhenAnyConfiguredColumnHasNoScore() {
        var score = GradeRules.finalScore(bothColumns(), Map.of(midtermId, new BigDecimal("8.00")));

        assertThat(score).isNull();
    }

    @Test
    void returnsNullWhenNoColumnHasAScore() {
        assertThat(GradeRules.finalScore(bothColumns(), Map.of())).isNull();
    }

    /** Review Focus #9: khóa chưa cấu hình cột điểm nào → không có điểm tổng kết để nói, trả null
     * thay vì 0 (0 sẽ bị đọc là "trượt"). */
    @Test
    void returnsNullWhenTheCourseHasNoGradeColumnsConfigured() {
        assertThat(GradeRules.finalScore(List.of(), Map.of())).isNull();
    }

    /** Điểm của một cột đã bị xoá khỏi cấu hình không được kéo điểm tổng kết lên: chỉ các cột ĐANG
     * cấu hình được tính, cột lạ trong map bị bỏ qua. */
    @Test
    void ignoresScoresOfColumnsThatAreNoLongerConfigured() {
        var removedColumnId = UUID.randomUUID();

        var score = GradeRules.finalScore(bothColumns(), Map.of(
                midtermId, new BigDecimal("8.00"), finalId, new BigDecimal("9.00"),
                removedColumnId, new BigDecimal("10.00")));

        assertThat(score).isEqualByComparingTo(new BigDecimal("86.00"));
    }

    @Test
    void roundsToTheScoreScaleOfTheDatabaseColumn() {
        var threeColumnsId = UUID.randomUUID();
        var oneThird = new GradeColumnResponse(threeColumnsId, "Đợt 1",
                new BigDecimal("33.33"), new BigDecimal("3.00"), 0);

        var score = GradeRules.finalScore(List.of(oneThird), Map.of(
                threeColumnsId, new BigDecimal("1.00")));

        assertThat(score.scale()).isEqualTo(2);
        assertThat(score).isEqualByComparingTo(new BigDecimal("11.11"));
    }

    /** Review Focus #10: biên của khoảng hợp lệ cho một cột, dùng lại ở RecordGrade. */
    @Test
    void acceptsScoresOnBothEndsOfTheRangeAndRejectsOutsideIt() {
        var maxScore = new BigDecimal("10.00");

        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("0.00"), maxScore)).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("10.00"), maxScore)).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("5.50"), maxScore)).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("-0.01"), maxScore)).isFalse();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("10.01"), maxScore)).isFalse();
    }

    /** Thang của RIÊNG cột quyết định: 9.5 hợp lệ ở thang 10 và sai ở thang 5. */
    @Test
    void judgesTheRangeAgainstTheMaxScoreOfThatColumnOnly() {
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("9.50"), new BigDecimal("10.00"))).isTrue();
        assertThat(GradeRules.isScoreWithinRange(new BigDecimal("9.50"), new BigDecimal("5.00"))).isFalse();
    }
}
