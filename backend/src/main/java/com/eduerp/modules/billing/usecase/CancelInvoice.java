package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.InvoiceNotPayableException;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Huỷ một hoá đơn tạo nhầm. CHỈ cho phép khi {@code UNPAID} - {@code PARTIALLY_PAID} nghĩa là đã có
 * tiền vào, phải hoàn tiền ngoài hệ thống trước; {@code OVERDUE} là công nợ thật đang chờ thu, huỷ
 * nó là xoá nợ chứ không phải sửa lỗi nhập liệu (spec mục 5). Dùng quyền {@code UPDATE_INVOICE}, KHÔNG
 * dùng {@code Actions.DELETE} - toàn hệ thống hiện chưa dùng DELETE ở bất kỳ resource nào.
 */
@Service
public class CancelInvoice {

    private final InvoiceRepository invoices;
    private final ApplicationEventPublisher events;

    CancelInvoice(InvoiceRepository invoices, ApplicationEventPublisher events) {
        this.invoices = invoices;
        this.events = events;
    }

    @Transactional
    public void execute(UUID invoiceId, UUID actorAccountId, UUID actorBranchId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (invoice.getStatus() != BillingConstants.InvoiceStatus.UNPAID) {
            throw new InvoiceNotPayableException(invoiceId, invoice.getStatus());
        }
        invoice.cancel();
    }
}
