package com.eduerp.modules.billing;

import org.springframework.http.HttpStatus;

/** Một "combo" một phần tử không phải combo - nó chỉ là một ghi danh, và đã có luồng CreateInvoice
 * cho trường hợp đó (spec mục 6 bước 1, Review Focus #2). */
public final class MinimumComboSizeException extends BillingException {
    public MinimumComboSizeException(int minimumEnrollments) {
        super("BILLING_COMBO_MINIMUM_SIZE", HttpStatus.BAD_REQUEST,
                "Combo phải gồm ít nhất " + minimumEnrollments + " ghi danh khác nhau");
    }
}
