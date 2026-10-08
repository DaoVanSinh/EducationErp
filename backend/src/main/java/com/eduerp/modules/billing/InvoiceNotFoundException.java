package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class InvoiceNotFoundException extends BillingException {
    public InvoiceNotFoundException(UUID invoiceId) {
        super("BILLING_INVOICE_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy hoá đơn " + invoiceId);
    }
}
