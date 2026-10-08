package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.identity.IdentityManagement;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.dto.PayrollRunDetailResponse;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.dto.PayslipResponse;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetPayrollRun {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final IdentityManagement identity;

    GetPayrollRun(PayrollRunRepository runs, PayslipRepository payslips, IdentityManagement identity) {
        this.runs = runs;
        this.payslips = payslips;
        this.identity = identity;
    }

    @Transactional(readOnly = true)
    public PayrollRunDetailResponse execute(UUID payrollRunId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        var runPayslips = payslips.findAllByPayrollRun_Id(payrollRunId);
        var accountIds = runPayslips.stream().map(Payslip::getAccountId).distinct().toList();
        var accountInfo = identity.summariesOf(accountIds);
        var payslipResponses = runPayslips.stream().map(p -> toPayslipResponse(p, accountInfo)).toList();
        var totalGross = runPayslips.stream().map(Payslip::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        var runResponse = new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), run.getStatus(),
                runPayslips.size(), totalGross);
        return new PayrollRunDetailResponse(runResponse, payslipResponses);
    }

    static PayslipResponse toPayslipResponse(Payslip p, Map<UUID, IdentityManagement.AccountBasicInfo> accountInfo) {
        var account = accountInfo.get(p.getAccountId());
        var netPay = PayrollRules.netPay(p.getGrossPay(), p.getAllowancesTotal(), p.getSocialInsuranceEmployee(),
                p.getIncomeTaxWithheld());
        return new PayslipResponse(p.getId(), p.getAccountId(), account == null ? null : account.fullName(),
                p.getContractType(), p.getGrossPay(), p.getSocialInsuranceEmployee(), p.getSocialInsuranceEmployer(),
                p.getIncomeTaxWithheld(), netPay, p.getHoursWorked(), p.isInProbation());
    }
}
