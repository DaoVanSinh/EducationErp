package com.eduerp.modules.courses.internal.rules;

import static org.assertj.core.api.Assertions.assertThat;

import com.eduerp.modules.courses.dto.GradeColumnSlot;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}/{@code PayrollRules}. */
class GradeColumnRulesTest {

    private static GradeColumnSlot slot(String name, String weightPercent, int displayOrder) {
        return new GradeColumnSlot(name, new BigDecimal(weightPercent), new BigDecimal("10.00"), displayOrder);
    }

    /** Review Focus #6, chiều hợp lệ: 40 + 60 = 100 đúng ví dụ của spec mục 2. */
    @Test
    void acceptsWeightsThatSumToExactlyOneHundred() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "60.00", 1)))).isTrue();
    }

    /** Review Focus #9: khóa học chưa cấu hình cột điểm nào là trạng thái hợp lệ - mọi Course tồn tại
     * trước V23 đều ở trạng thái này, chặn ở đây là khiến chúng không sửa được nữa. */
    @Test
    void acceptsAnEmptyColumnListAsNotYetConfigured() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(List.of())).isTrue();
    }

    /** Review Focus #6, chiều bị chặn: thiếu. */
    @Test
    void rejectsWeightsThatSumBelowOneHundred() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "50.00", 1)))).isFalse();
    }

    /** Review Focus #6, chiều bị chặn: vượt. */
    @Test
    void rejectsWeightsThatSumAboveOneHundred() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "70.00", 1)))).isFalse();
    }

    /** Spec mục 4: "sai lệch do làm tròn chấp nhận trong khoảng ±0.01" - ba cột 33.33 + 33.33 + 33.34
     * cộng đúng 100.00, còn 33.33 x 3 = 99.99 lệch đúng 0.01 nên vẫn phải qua. */
    @Test
    void acceptsATotalOffByAtMostTheRoundingTolerance() {
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(List.of(
                slot("Đợt 1", "33.33", 0), slot("Đợt 2", "33.33", 1), slot("Đợt 3", "33.33", 2))))
                .as("99.99 lệch đúng 0.01 - trong ngưỡng")
                .isTrue();
        assertThat(GradeColumnRules.weightsSumToRequiredTotal(List.of(
                slot("Đợt 1", "33.33", 0), slot("Đợt 2", "33.33", 1), slot("Đợt 3", "33.32", 2))))
                .as("99.98 lệch 0.02 - ngoài ngưỡng")
                .isFalse();
    }

    /** Thông báo lỗi phải nói ra con số thật người dùng vừa nhập, nên tổng là một hàm riêng dùng được. */
    @Test
    void totalWeightAddsUpEveryColumn() {
        assertThat(GradeColumnRules.totalWeight(
                List.of(slot("Giữa kỳ", "40.00", 0), slot("Cuối kỳ", "55.50", 1))))
                .isEqualByComparingTo(new BigDecimal("95.50"));
    }

    @Test
    void totalWeightOfAnEmptyListIsZero() {
        assertThat(GradeColumnRules.totalWeight(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
