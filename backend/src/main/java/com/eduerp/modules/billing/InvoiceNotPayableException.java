package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Dùng cho cả ba trường hợp: thu online, thu thủ công, và huỷ hoá đơn khi trạng thái không cho phép. */
public final class InvoiceNotPayableException extends BillingException {
    public InvoiceNotPayableException(UUID invoiceId, BillingConstants.InvoiceStatus status) {
        super("BILLING_INVOICE_NOT_PAYABLE", HttpStatus.CONFLICT,
                "Hoá đơn " + invoiceId + " đang ở trạng thái " + status + ", không thực hiện được thao tác này");
    }
}
