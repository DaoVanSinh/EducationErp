package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Review Focus #4: chưa cấu hình bậc nào thoả số khoá này thì CHẶN, không mặc định 0% - mirror
 * CourseTuitionNotConfiguredException ("giá chưa cấu hình thì chặn, không suy đoán"). */
public final class ComboDiscountTierNotConfiguredException extends BillingException {
    public ComboDiscountTierNotConfiguredException(int courseCount) {
        super("BILLING_COMBO_DISCOUNT_TIER_NOT_CONFIGURED", HttpStatus.CONFLICT,
                "Chưa cấu hình bậc giảm giá nào cho combo " + courseCount + " khoá");
    }
}
