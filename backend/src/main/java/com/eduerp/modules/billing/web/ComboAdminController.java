package com.eduerp.modules.billing.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.billing.dto.ComboDetailResponse;
import com.eduerp.modules.billing.dto.ComboResponse;
import com.eduerp.modules.billing.dto.CreateComboInvoiceRequest;
import com.eduerp.modules.billing.dto.CreateComboRequest;
import com.eduerp.modules.billing.dto.InvoiceResponse;
import com.eduerp.modules.billing.usecase.CancelCombo;
import com.eduerp.modules.billing.usecase.CreateCombo;
import com.eduerp.modules.billing.usecase.CreateComboInvoice;
import com.eduerp.modules.billing.usecase.GetComboDetail;
import com.eduerp.modules.billing.usecase.ListCombos;
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
 * Quản trị combo học phí - mirror {@code InvoiceAdminController} 1:1 về cấu trúc.
 *
 * <p>KHÔNG có resource RBAC riêng (spec mục 8): combo là một hình thức thu học phí, nên dùng lại
 * đúng {@code CREATE_INVOICE} (tạo combo, phát hành đợt thu), {@code READ_INVOICE} (xem) và
 * {@code UPDATE_INVOICE} (huỷ combo - chuyển state, hệ thống chưa dùng {@code Actions.DELETE} ở đâu).
 *
 * <p>Đợt thu của combo nằm ở {@code POST /invoices} với {@code comboId} TRONG BODY, mirror đúng
 * {@code POST /api/billing/invoices} vốn nhận {@code enrollmentId} trong body - id của đơn vị thu là
 * một phần của phiếu thu, không phải một đoạn đường dẫn.
 */
@RestController
@RequestMapping("/api/billing/combos")
class ComboAdminController {

    private final ListCombos listCombos;
    private final CreateCombo createCombo;
    private final GetComboDetail getComboDetail;
    private final CreateComboInvoice createComboInvoice;
    private final CancelCombo cancelCombo;

    ComboAdminController(ListCombos listCombos, CreateCombo createCombo, GetComboDetail getComboDetail,
            CreateComboInvoice createComboInvoice, CancelCombo cancelCombo) {
        this.listCombos = listCombos;
        this.createCombo = createCombo;
        this.getComboDetail = getComboDetail;
        this.createComboInvoice = createComboInvoice;
        this.cancelCombo = cancelCombo;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    PageResponse<ComboResponse> list(@PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) UUID studentProfileId) {
        return listCombos.execute(pageable, studentProfileId);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    ComboResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateComboRequest request) {
        return createCombo.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    /** Đặt TRƯỚC {@code /{comboId}} không cần thiết (khác HTTP method), nhưng giữ cạnh nhóm tạo để
     * người đọc thấy đây là endpoint ghi của combo. */
    @PostMapping("/invoices")
    @PreAuthorize(AccessConstants.AccessRules.CREATE_INVOICE)
    InvoiceResponse createInvoice(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreateComboInvoiceRequest request) {
        return createComboInvoice.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{comboId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_INVOICE)
    ComboDetailResponse get(@PathVariable UUID comboId) {
        return getComboDetail.execute(comboId);
    }

    @PostMapping("/{comboId}/cancel")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_INVOICE)
    void cancel(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID comboId) {
        cancelCombo.execute(comboId, principal.accountId(), principal.homeBranchId());
    }
}
