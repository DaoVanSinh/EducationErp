package com.eduerp.modules.billing;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Final review Critical #2: huỷ một hoá đơn còn một giao dịch online PENDING mở đường cho callback
 * đến muộn hồi sinh hoá đơn đã huỷ - chặn ở đây trước khi điều đó xảy ra. */
public final class InvoiceHasPendingPaymentException extends BillingException {
    public InvoiceHasPendingPaymentException(UUID invoiceId) {
        super("BILLING_INVOICE_HAS_PENDING_PAYMENT", HttpStatus.CONFLICT,
                "Hoá đơn " + invoiceId + " đang có giao dịch online chờ xác nhận, không thể huỷ");
    }
}
