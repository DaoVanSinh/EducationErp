package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Combo đã có hoá đơn sống đủ số tiền sau giảm giá, không còn gì để lập thêm. */
public final class ComboAlreadyInvoicedException extends BillingException {
    public ComboAlreadyInvoicedException(UUID comboId) {
        super("BILLING_COMBO_ALREADY_INVOICED", HttpStatus.CONFLICT, "Combo đã có đủ hoá đơn");
    }
}
