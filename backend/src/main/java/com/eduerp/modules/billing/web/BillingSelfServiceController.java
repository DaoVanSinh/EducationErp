package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentRequest;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.dto.InvoiceDetailResponse;
import com.eduerp.modules.billing.usecase.InitiateMyOnlinePayment;
import com.eduerp.modules.billing.usecase.ListMyInvoices;
import com.eduerp.shared.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cổng học phí của học viên - hoá đơn và lịch sử thanh toán của CHÍNH người gọi, và tự thanh toán
 * online hoá đơn đó. KHÁC {@code InvoiceAdminController} (đòi {@code READ_INVOICE}/
 * {@code UPDATE_INVOICE} scope ORGANIZATION): ở đây hồ sơ học viên luôn suy ra từ
 * {@code principal.accountId()}, nên quyền cổng học tập ({@code READ_LEARNING}) là đủ.
 */
@RestController
@RequestMapping("/api/billing/me")
class BillingSelfServiceController {

    private final ListMyInvoices listMyInvoices;
    private final InitiateMyOnlinePayment initiateMyOnlinePayment;

    BillingSelfServiceController(ListMyInvoices listMyInvoices, InitiateMyOnlinePayment initiateMyOnlinePayment) {
        this.listMyInvoices = listMyInvoices;
        this.initiateMyOnlinePayment = initiateMyOnlinePayment;
    }

    @GetMapping("/invoices")
    @PreAuthorize(AccessConstants.AccessRules.READ_LEARNING)
    List<InvoiceDetailResponse> myInvoices(@AuthenticationPrincipal AccountPrincipal principal) {
        return listMyInvoices.execute(principal.accountId());
    }

    @PostMapping("/invoices/{invoiceId}/online-payment")
    @PreAuthorize(AccessConstants.AccessRules.READ_LEARNING)
    InitiateOnlinePaymentResponse payMyInvoice(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID invoiceId, @Valid @RequestBody InitiateOnlinePaymentRequest request) {
        return initiateMyOnlinePayment.execute(principal.accountId(), invoiceId, request.gateway());
    }
}
