package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvalidPaymentAmountException;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Kế toán nhập tay tiền mặt/chuyển khoản. Không có {@code gatewayTransactionId} - cột đó nullable
 * và unique index của Postgres không coi hai NULL là trùng, nên nhiều lần thu tay cùng tồn tại. */
@Service
public class RecordManualPayment {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final ApplicationEventPublisher events;

    RecordManualPayment(InvoiceRepository invoices, PaymentRepository payments,
            ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.payments = payments;
        this.events = events;
    }

    @Transactional
    public InvoiceResponse execute(UUID invoiceId, BigDecimal amount, UUID actorAccountId, UUID actorBranchId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (!BillingRules.isPayable(invoice.getStatus())) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus());
        }
        var remaining = BillingRules.remaining(invoice.getAmount(), invoice.getAmountPaid());
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new InvalidPaymentAmountException(amount);
        }

        payments.save(new Payment(invoice, amount, BillingConstants.PaymentMethod.MANUAL, null,
                BillingConstants.PaymentStatus.SUCCESS));
        invoice.applyPayment(amount);
        events.publishEvent(new BillingEvents.PaymentReceived(invoiceId, actorAccountId, actorBranchId));
        return CreateInvoice.toResponse(invoice);
    }
}
