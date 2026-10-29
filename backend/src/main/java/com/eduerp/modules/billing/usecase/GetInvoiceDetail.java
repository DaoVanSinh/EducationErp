package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.dto.InvoiceDetailResponse;
import com.eduerp.modules.billing.dto.PaymentResponse;
import com.eduerp.modules.billing.internal.model.Payment;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import com.eduerp.modules.billing.internal.repository.PaymentRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirror {@code GetPayrollRun}: tổng thể + danh sách con, một query cho mỗi phần, không N+1. */
@Service
public class GetInvoiceDetail {

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;

    private final BillingBranchGuard branchGuard;

    GetInvoiceDetail(InvoiceRepository invoices, PaymentRepository payments, BillingBranchGuard branchGuard) {
        this.invoices = invoices;
        this.payments = payments;
        this.branchGuard = branchGuard;
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse execute(UUID actorAccountId, UUID invoiceId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.READ, invoice.getBranchId());
        // Thứ tự createdAt DESC do chính query quyết định - không sort lại ở đây.
        var history = payments.findAllByInvoice_IdOrderByCreatedAtDesc(invoiceId).stream()
                .map(GetInvoiceDetail::toPaymentResponse).toList();
        return new InvoiceDetailResponse(CreateInvoice.toResponse(invoice), history);
    }

    /** Dùng lại ở {@code GetPaymentStatus} - một chỗ map duy nhất cho Payment. */
    static PaymentResponse toPaymentResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getAmount(), payment.getMethod(), payment.getStatus(),
                payment.getPaidAt(), payment.getCreatedAt());
    }
}
