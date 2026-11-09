package com.eduerp.modules.payroll.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.payroll.dto.BranchPayslipResponse;
import com.eduerp.modules.payroll.dto.MarkPayslipPaidRequest;
import com.eduerp.modules.payroll.dto.UndoPayslipPaymentRequest;
import com.eduerp.modules.payroll.usecase.ListBranchPayslips;
import com.eduerp.modules.payroll.usecase.MarkPayslipPaid;
import com.eduerp.modules.payroll.usecase.UndoPayslipPayment;
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

/** Chi trả lương theo chi nhánh: nhận phiếu đã duyệt từ admin gốc rồi đánh dấu đã thanh toán. */
@RestController
@RequestMapping("/api/payroll/branch-payslips")
class PayrollBranchController {

    private final ListBranchPayslips listBranchPayslips;
    private final MarkPayslipPaid markPayslipPaid;
    private final UndoPayslipPayment undoPayslipPayment;

    PayrollBranchController(ListBranchPayslips listBranchPayslips, MarkPayslipPaid markPayslipPaid,
            UndoPayslipPayment undoPayslipPayment) {
        this.listBranchPayslips = listBranchPayslips;
        this.markPayslipPaid = markPayslipPaid;
        this.undoPayslipPayment = undoPayslipPayment;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_BRANCH_PAYSLIPS)
    PageResponse<BranchPayslipResponse> list(@AuthenticationPrincipal AccountPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable, @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Boolean paid) {
        return listBranchPayslips.execute(principal.accountId(), pageable, branchId, year, month, paid);
    }

    @PostMapping("/{payslipId}/pay")
    @PreAuthorize(AccessConstants.AccessRules.PAY_BRANCH_PAYSLIPS)
    void pay(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID payslipId,
            @Valid @RequestBody MarkPayslipPaidRequest request) {
        markPayslipPaid.execute(payslipId, principal.accountId(), principal.homeBranchId(), request);
    }

    @PostMapping("/{payslipId}/undo-payment")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    void undoPayment(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID payslipId,
            @Valid @RequestBody UndoPayslipPaymentRequest request) {
        undoPayslipPayment.execute(payslipId, principal.accountId(), principal.homeBranchId(), request);
    }
}
