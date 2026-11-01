package com.eduerp.modules.billing.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.InvoiceNotFoundException;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.internal.OnlinePaymentStarter;
import com.eduerp.modules.billing.internal.access.BillingBranchGuard;
import com.eduerp.modules.billing.internal.repository.InvoiceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kế toán tạo link/QR thanh toán cho phần CÒN LẠI của hoá đơn (gửi cho phụ huynh/học viên). Chỉ khác
 * {@code InitiateMyOnlinePayment} ở bước kiểm quyền: ở đây là phạm vi chi nhánh của người thu. Phần
 * tạo link + ghi {@code Payment} PENDING nằm ở {@link OnlinePaymentStarter}.
 */
@Service
public class InitiateOnlinePayment {

    private final InvoiceRepository invoices;
    private final BillingBranchGuard branchGuard;
    private final OnlinePaymentStarter starter;

    InitiateOnlinePayment(InvoiceRepository invoices, BillingBranchGuard branchGuard, OnlinePaymentStarter starter) {
        this.invoices = invoices;
        this.branchGuard = branchGuard;
        this.starter = starter;
    }

    @Transactional
    public InitiateOnlinePaymentResponse execute(UUID invoiceId, BillingConstants.PaymentMethod gateway,
            UUID actorAccountId, UUID actorBranchId) {
        var invoice = invoices.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        branchGuard.require(actorAccountId, AccessConstants.Actions.UPDATE, invoice.getBranchId());
        return starter.start(invoice, gateway);
    }
}
