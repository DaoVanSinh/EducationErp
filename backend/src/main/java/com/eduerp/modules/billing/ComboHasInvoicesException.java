package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Review Focus #6: huỷ combo là xoá cứng. Một khi đã phát hành hoá đơn (bất kể trạng thái, kể cả
 * đã huỷ), combo là chứng từ tài chính đã chốt - cùng nguyên tắc "đã chốt thì không xoá" mà
 * CancelInvoice áp dụng cho hoá đơn (spec mục 6). */
public final class ComboHasInvoicesException extends BillingException {
    public ComboHasInvoicesException(UUID comboId) {
        super("BILLING_COMBO_HAS_INVOICES", HttpStatus.CONFLICT,
                "Combo " + comboId + " đã phát hành hoá đơn, không huỷ được");
    }
}
