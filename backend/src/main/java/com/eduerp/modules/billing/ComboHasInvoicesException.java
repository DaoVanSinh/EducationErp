package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Huỷ combo xoá cứng cả hoá đơn của nó, nên chỉ được làm khi chưa có đồng tiền hay giao dịch nào: dấu vết thu tiền
 * là chứng từ tài chính, không được mất. */
public final class ComboHasInvoicesException extends BillingException {
    public ComboHasInvoicesException(UUID comboId) {
        super("BILLING_COMBO_HAS_INVOICES", HttpStatus.CONFLICT,
                "Combo đã có giao dịch thanh toán, không huỷ được");
    }
}
