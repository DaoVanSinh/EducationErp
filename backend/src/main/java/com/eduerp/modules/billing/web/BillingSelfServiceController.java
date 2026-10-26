package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.dto.InvoiceDetailResponse;
import com.eduerp.modules.billing.usecase.ListMyInvoices;
import com.eduerp.shared.AccountPrincipal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cổng học phí của học viên - hoá đơn và lịch sử thanh toán của CHÍNH người gọi. KHÁC
 * {@code InvoiceAdminController} (đòi {@code READ_INVOICE} scope ORGANIZATION): ở đây hồ sơ học viên
 * luôn suy ra từ {@code principal.accountId()}.
 */
@RestController
@RequestMapping("/api/billing/me")
class BillingSelfServiceController {

    private final ListMyInvoices listMyInvoices;

    BillingSelfServiceController(ListMyInvoices listMyInvoices) {
        this.listMyInvoices = listMyInvoices;
    }

    @GetMapping("/invoices")
    @PreAuthorize(AccessConstants.AccessRules.READ_LEARNING)
    List<InvoiceDetailResponse> myInvoices(@AuthenticationPrincipal AccountPrincipal principal) {
        return listMyInvoices.execute(principal.accountId());
    }
}
