package com.eduerp.modules.payroll.web;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.PayrollRunDetailResponse;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.dto.RejectPayrollRunRequest;
import com.eduerp.modules.payroll.dto.UpdatePayslipRequest;
import com.eduerp.modules.payroll.usecase.ApprovePayrollRun;
import com.eduerp.modules.payroll.usecase.CreatePayrollRun;
import com.eduerp.modules.payroll.usecase.GetPayrollRun;
import com.eduerp.modules.payroll.usecase.ListPayrollRuns;
import com.eduerp.modules.payroll.usecase.RejectPayrollRun;
import com.eduerp.modules.payroll.usecase.SubmitPayrollRunForApproval;
import com.eduerp.modules.payroll.usecase.UpdatePayslip;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Quản trị kỳ lương - mirror CourseAdminController 1:1 về cấu trúc. */
@RestController
@RequestMapping("/api/payroll/runs")
class PayrollRunAdminController {

    private final ListPayrollRuns listPayrollRuns;
    private final CreatePayrollRun createPayrollRun;
    private final GetPayrollRun getPayrollRun;
    private final UpdatePayslip updatePayslip;
    private final SubmitPayrollRunForApproval submitPayrollRunForApproval;
    private final ApprovePayrollRun approvePayrollRun;
    private final RejectPayrollRun rejectPayrollRun;

    PayrollRunAdminController(ListPayrollRuns listPayrollRuns, CreatePayrollRun createPayrollRun,
            GetPayrollRun getPayrollRun, UpdatePayslip updatePayslip,
            SubmitPayrollRunForApproval submitPayrollRunForApproval, ApprovePayrollRun approvePayrollRun,
            RejectPayrollRun rejectPayrollRun) {
        this.listPayrollRuns = listPayrollRuns;
        this.createPayrollRun = createPayrollRun;
        this.getPayrollRun = getPayrollRun;
        this.updatePayslip = updatePayslip;
        this.submitPayrollRunForApproval = submitPayrollRunForApproval;
        this.approvePayrollRun = approvePayrollRun;
        this.rejectPayrollRun = rejectPayrollRun;
    }

    @GetMapping
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    PageResponse<PayrollRunResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return listPayrollRuns.execute(pageable);
    }

    @PostMapping
    @PreAuthorize(AccessConstants.AccessRules.CREATE_PAYROLL)
    PayrollRunResponse create(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody CreatePayrollRunRequest request) {
        return createPayrollRun.execute(principal.accountId(), principal.homeBranchId(), request);
    }

    @GetMapping("/{runId}")
    @PreAuthorize(AccessConstants.AccessRules.READ_PAYROLL)
    PayrollRunDetailResponse get(@PathVariable UUID runId) {
        return getPayrollRun.execute(runId);
    }

    @PatchMapping("/{runId}/payslips/{payslipId}")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    PayslipResponse updatePayslip(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId,
            @PathVariable UUID payslipId, @Valid @RequestBody UpdatePayslipRequest request) {
        return updatePayslip.execute(payslipId, principal.accountId(), principal.homeBranchId(), request);
    }

    @PostMapping("/{runId}/submit")
    @PreAuthorize(AccessConstants.AccessRules.UPDATE_PAYROLL)
    void submit(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId) {
        submitPayrollRunForApproval.execute(runId, principal.accountId(), principal.homeBranchId());
    }

    @PostMapping("/{runId}/approve")
    @PreAuthorize(AccessConstants.AccessRules.APPROVE_PAYROLL)
    void approve(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId) {
        approvePayrollRun.execute(runId, principal.accountId(), principal.homeBranchId());
    }

    @PostMapping("/{runId}/reject")
    @PreAuthorize(AccessConstants.AccessRules.APPROVE_PAYROLL)
    void reject(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID runId,
            @Valid @RequestBody RejectPayrollRunRequest request) {
        rejectPayrollRun.execute(runId, request.reason(), principal.accountId(), principal.homeBranchId());
    }
}
