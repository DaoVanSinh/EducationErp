package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** {@code combo_discount_tiers.min_course_count} UNIQUE (V21): hai bậc cùng mốc sẽ khiến việc chọn
 * bậc phụ thuộc thứ tự dòng trong bảng. Sửa bậc đã có thì dùng UpdateComboDiscountTier. */
public final class ComboDiscountTierAlreadyExistsException extends BillingException {
    public ComboDiscountTierAlreadyExistsException(int minCourseCount) {
        super("BILLING_COMBO_DISCOUNT_TIER_ALREADY_EXISTS", HttpStatus.CONFLICT,
                "Đã có bậc giảm giá cho mốc " + minCourseCount + " khoá");
    }
}
