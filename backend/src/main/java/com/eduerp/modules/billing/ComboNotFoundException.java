package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class ComboNotFoundException extends BillingException {
    public ComboNotFoundException(UUID comboId) {
        super("BILLING_COMBO_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy combo " + comboId);
    }
}
