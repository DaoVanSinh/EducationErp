package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.internal.model.Invoice;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent tự nhiên: sau khi đổi sang {@code OVERDUE}, hoá đơn không còn nằm trong tập quét
 * ({@code UNPAID}/{@code PARTIALLY_PAID}) nên lần chạy sau không thấy nữa (spec mục 5).
 *
 * <p>Không nhận request, không nhận actor: hệ thống tự sinh, {@code InvoiceOverdue.actorAccountId}
 * là {@code null}.
 */
@Service
public class MarkOverdueInvoices {

    private static final List<BillingConstants.InvoiceStatus> SCANNED_STATUSES = List.of(
            BillingConstants.InvoiceStatus.UNPAID, BillingConstants.InvoiceStatus.PARTIALLY_PAID);

    private final InvoiceRepository invoices;
    private final ApplicationEventPublisher events;

    MarkOverdueInvoices(InvoiceRepository invoices, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.events = events;
    }

    /** @return số hoá đơn vừa được đánh dấu quá hạn - để scheduler log lại được một con số thật. */
    @Transactional
    public int execute() {
        var candidates = invoices.findAllByStatusInAndDueDateBefore(SCANNED_STATUSES, LocalDate.now());
        candidates.forEach(this::markOverdue);
        return candidates.size();
    }

    private void markOverdue(Invoice invoice) {
        invoice.markOverdue();
        events.publishEvent(new BillingEvents.InvoiceOverdue(invoice.getId(), null, invoice.getBranchId()));
    }
}
