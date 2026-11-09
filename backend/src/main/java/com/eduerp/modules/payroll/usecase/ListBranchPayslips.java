package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.access.AccessConstants;
import com.eduerp.modules.access.AccessManagement;
import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.dto.BranchPayslipResponse;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.response.PayrollResponseAssembler;
import com.eduerp.shared.PageResponse;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Phiếu đã duyệt để chi trả. Admin chi nhánh luôn bị ép về chi nhánh của mình, admin gốc lọc theo chi nhánh đang chọn. */
@Service
public class ListBranchPayslips {

    private final PayslipRepository payslips;
    private final AccessManagement access;
    private final IdentityManagement identity;
    private final OrganizationManagement organization;

    ListBranchPayslips(PayslipRepository payslips, AccessManagement access, IdentityManagement identity,
            OrganizationManagement organization) {
        this.payslips = payslips;
        this.access = access;
        this.identity = identity;
        this.organization = organization;
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchPayslipResponse> execute(UUID actorAccountId, Pageable pageable, UUID requestedBranchId,
            Integer year, Integer month, Boolean paid) {
        var branchId = access.dataScopeOf(actorAccountId, AccessConstants.Resources.PAYROLL,
                AccessConstants.Actions.READ).branchFilter(requestedBranchId);
        var page = payslips.searchApproved(PayrollConstants.PayrollRunStatus.APPROVED, branchId, year, month, paid,
                pageable);
        var accountInfo = identity.summariesOf(page.getContent().stream().map(Payslip::getAccountId).toList());
        var branchNames = organization.namesOf(page.getContent().stream().map(Payslip::getBranchId)
                .filter(Objects::nonNull).distinct().toList());
        return PageResponse.of(page.map(payslip -> new BranchPayslipResponse(payslip.getPayrollRun().getYear(),
                payslip.getPayrollRun().getMonth(),
                PayrollResponseAssembler.toPayslipResponse(payslip, accountInfo, branchNames))));
    }
}
