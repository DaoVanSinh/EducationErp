package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.shared.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ba filter optional dồn vào một query ở repository - usecase không rẽ nhánh nào (complexity 1). */
@Service
public class ListInvoices {

    private final InvoiceRepository invoices;

    ListInvoices(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> execute(Pageable pageable, UUID studentProfileId, UUID enrollmentId,
            BillingConstants.InvoiceStatus status) {
        return PageResponse.of(invoices.search(studentProfileId, enrollmentId, status, pageable)
                .map(CreateInvoice::toResponse));
    }
}
