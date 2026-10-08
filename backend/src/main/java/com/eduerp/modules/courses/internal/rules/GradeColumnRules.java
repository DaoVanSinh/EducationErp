package com.eduerp.modules.courses.internal.rules;

import com.eduerp.modules.courses.CoursesConstants;
import com.eduerp.modules.courses.dto.GradeColumnSlot;
import java.math.BigDecimal;
import java.util.List;

/** Pure, 100% unit-test được bằng new, không I/O - mirror {@code BillingRules}. */
public final class GradeColumnRules {

    private GradeColumnRules() {
    }

    public static BigDecimal totalWeight(List<GradeColumnSlot> columns) {
        return columns.stream().map(GradeColumnSlot::weightPercent)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Danh sách RỖNG là hợp lệ và có nghĩa "khóa học chưa cấu hình cột điểm": mọi {@code Course} tồn
     * tại trước V23 ở đúng trạng thái đó, chặn ở đây là khiến chúng không sửa được nữa (Review Focus
     * #9). Với danh sách không rỗng thì tổng phải bằng 100.00 trong sai số ±0.01 (spec mục 2.3 + 4).
     */
    public static boolean weightsSumToRequiredTotal(List<GradeColumnSlot> columns) {
        if (columns.isEmpty()) {
            return true;
        }
        return totalWeight(columns).subtract(CoursesConstants.GradeColumns.REQUIRED_TOTAL_WEIGHT_PERCENT)
                .abs()
                .compareTo(CoursesConstants.GradeColumns.WEIGHT_TOLERANCE) <= 0;
    }
}
