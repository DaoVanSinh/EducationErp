package com.eduerp.modules.billing.usecase;

import com.eduerp.core.exception.AppValidationException;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.BillingEvents;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.dto.ExtendInvoiceDueRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.response.InvoiceResponseAssembler;
import com.eduerp.modules.billing.internal.rules.BillingRules;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin gốc gia hạn học phí cho một trường hợp quá hạn, kèm lý do. Gia hạn xong hoá đơn rời danh sách quá hạn. */
@Service
public class ExtendInvoiceDue {

    private final InvoiceRepository invoices;
    private final InvoiceResponseAssembler assembler;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    ExtendInvoiceDue(InvoiceRepository invoices, InvoiceResponseAssembler assembler, ApplicationEventPublisher events) {
        this(invoices, assembler, events, Clock.system(ZoneId.of(BillingConstants.Schedules.CENTER_ZONE_ID)));
    }

    ExtendInvoiceDue(InvoiceRepository invoices, InvoiceResponseAssembler assembler, ApplicationEventPublisher events,
            Clock clock) {
        this.invoices = invoices;
        this.assembler = assembler;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public InvoiceResponse execute(UUID invoiceId, UUID actorAccountId, UUID actorBranchId,
            ExtendInvoiceDueRequest request) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (!BillingRules.isPayable(invoice.getStatus())) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus());
        }
        if (!request.dueDate().isAfter(LocalDate.now(clock))) {
            throw new AppValidationException("dueDate", "Hạn mới phải sau hôm nay");
        }
        invoice.extendDue(request.dueDate(), request.reason().strip());
        events.publishEvent(new BillingEvents.InvoiceDueExtended(invoiceId, actorAccountId, actorBranchId));
        return assembler.toResponse(invoice);
    }
}
