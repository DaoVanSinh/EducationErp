package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.organization.OrganizationManagement;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.dto.MissingPayTermsResponse;
import com.eduerp.modules.payroll.dto.PayrollRunDetailResponse;
import com.eduerp.modules.payroll.internal.PayslipSynchronizer;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.response.PayrollResponseAssembler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPayrollRun {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final IdentityManagement identity;
    private final PayslipSynchronizer synchronizer;
    private final OrganizationManagement organization;

    GetPayrollRun(PayrollRunRepository runs, PayslipRepository payslips, IdentityManagement identity,
            PayslipSynchronizer synchronizer, OrganizationManagement organization) {
        this.organization = organization;
        this.runs = runs;
        this.payslips = payslips;
        this.identity = identity;
        this.synchronizer = synchronizer;
    }

    @Transactional(readOnly = true)
    public PayrollRunDetailResponse execute(UUID payrollRunId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        var runPayslips = payslips.findAllByPayrollRun_Id(payrollRunId);
        var missing = run.getStatus() == PayrollConstants.PayrollRunStatus.DRAFT
                ? synchronizer.missingPayTerms(run.getPeriod()) : Map.<UUID, Integer>of();

        var accountIds = Stream.concat(runPayslips.stream().map(Payslip::getAccountId), missing.keySet().stream())
                .distinct().toList();
        var accountInfo = identity.summariesOf(accountIds);

        var branchNames = organization.namesOf(runPayslips.stream().map(Payslip::getBranchId)
                .filter(Objects::nonNull).distinct().toList());
        var payslipResponses = runPayslips.stream()
                .map(payslip -> PayrollResponseAssembler.toPayslipResponse(payslip, accountInfo, branchNames))
                .toList();
        List<MissingPayTermsResponse> missingResponses = missing.entrySet().stream()
                .map(entry -> {
                    var account = accountInfo.get(entry.getKey());
                    return new MissingPayTermsResponse(entry.getKey(), account == null ? null : account.fullName(),
                            entry.getValue());
                })
                .toList();
        return new PayrollRunDetailResponse(PayrollResponseAssembler.toRunResponse(run, runPayslips),
                payslipResponses, missingResponses,
                PayrollResponseAssembler.toBranchSummaries(runPayslips, branchNames));
    }
}
