package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ComboDiscountTierNotFoundException extends BillingException {
    public ComboDiscountTierNotFoundException(UUID tierId) {
        super("BILLING_COMBO_DISCOUNT_TIER_NOT_FOUND", HttpStatus.NOT_FOUND,
                "Không tìm thấy bậc giảm giá " + tierId);
    }
}
