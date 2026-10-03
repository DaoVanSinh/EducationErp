package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.BillingConstants;
import com.eduerp.modules.billing.dto.CreateInvoiceRequest;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentRequest;
import com.eduerp.modules.billing.dto.InitiateOnlinePaymentResponse;
import com.eduerp.modules.billing.dto.InvoiceDetailResponse;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.dto.RecordManualPaymentRequest;
import com.eduerp.modules.billing.usecase.CancelInvoice;
import com.eduerp.modules.billing.usecase.CreateInvoice;
import com.eduerp.modules.billing.usecase.GetInvoiceDetail;
import com.eduerp.modules.billing.usecase.InitiateOnlinePayment;
import com.eduerp.modules.billing.usecase.ListInvoices;
import com.eduerp.modules.billing.usecase.RecordManualPayment;
import com.eduerp.shared.AccountPrincipal;
import com.eduerp.shared.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quản trị hoá đơn học phí - mirror PayrollRunAdminController 1:1 về cấu trúc. Huỷ hoá đơn dùng
 * {@code UPDATE_INVOICE} (không có {@code Actions.DELETE} ở bất kỳ resource nào trong hệ thống này).
 */
@RestController
@RequestMapping("/api/billing/invoices")
class InvoiceAdminController {

    private final ListInvoices listInvoices;
    private final CreateInvoice createInvoice;
    private final GetInvoiceDetail getInvoiceDetail;
    private final InitiateOnlinePayment initiateOnlinePayment;
    private final RecordManualPayment recordManualPayment;
    private final CancelInvoice cancelInvoice;

    InvoiceAdminController(ListInvoices listInvoices, CreateInvoice createInvoice,
            GetInvoiceDetail getInvoiceDetail, InitiateOnlinePayment initiateOnlinePayment,
            RecordManualPayment recordManualPayment, CancelInvoice cancelInvoice) {
        this.listInvoices = listInvoices;
        this.createInvoice = createInvoice;
        this.getInvoiceDetail = getInvoiceDetail;
        this.initiateOnlinePayment = initiateOnlinePayment;
        this.recordManualPayment = recordManualPayment;
        this.cancelInvoice = cancelInvoice;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    PageResponse<InvoiceResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID studentProfileId,
            @RequestParam(required = false) UUID enrollmentId,
            @RequestParam(required = false) BillingConstants.InvoiceStatus status) {
        return listInvoices.execute(pageable, studentProfileId, enrollmentId, status);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    InvoiceResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateInvoiceRequest request) {
        return createInvoice.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    InvoiceDetailResponse get(@PathVariable UUID invoiceId) {
        return getInvoiceDetail.execute(invoiceId);
    }

    @PostMapping("/{invoiceId}/online-payment")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    InitiateOnlinePaymentResponse initiateOnlinePayment(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID invoiceId, @Valid @RequestBody InitiateOnlinePaymentRequest request) {
        return initiateOnlinePayment.execute(invoiceId, request.gateway(), principal.accountId(),
                principal.homeBranchId());
    }

    @PostMapping("/{invoiceId}/manual-payment")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    InvoiceResponse recordManualPayment(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID invoiceId, @Valid @RequestBody RecordManualPaymentRequest request) {
        return recordManualPayment.execute(invoiceId, request.amount(), principal.accountId(),
                principal.homeBranchId());
    }

    @PostMapping("/{invoiceId}/cancel")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    void cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID invoiceId) {
        cancelInvoice.execute(invoiceId, principal.accountId(), principal.homeBranchId());
    }
}
