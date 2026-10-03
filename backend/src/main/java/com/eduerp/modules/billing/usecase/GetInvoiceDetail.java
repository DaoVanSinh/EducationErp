package com.eduerp.modules.billing.usecase;

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

    GetInvoiceDetail(InvoiceRepository invoices, PaymentRepository payments) {
        this.invoices = invoices;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public InvoiceDetailResponse execute(UUID invoiceId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
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
