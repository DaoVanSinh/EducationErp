package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunAlreadyExistsException;
import com.eduerp.modules.payroll.dto.CreatePayrollRunRequest;
import com.eduerp.modules.payroll.dto.PayrollRunResponse;
import com.eduerp.modules.payroll.internal.model.EmploymentContract;
import com.eduerp.modules.payroll.internal.model.PayrollRun;
import com.eduerp.modules.payroll.internal.model.Payslip;
import com.eduerp.modules.payroll.internal.repository.EmploymentContractRepository;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import com.eduerp.modules.payroll.internal.rules.PayrollRules;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Review Focus #5: KHÔNG phụ thuộc IdentityManagement - chỉ lọc EmploymentContract.status, không
 * thể và không được truy vấn Account.status (spec mục 6.5). */
@Service
public class CreatePayrollRun {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final EmploymentContractRepository contracts;

    CreatePayrollRun(PayrollRunRepository runs, PayslipRepository payslips, EmploymentContractRepository contracts) {
        this.runs = runs;
        this.payslips = payslips;
        this.contracts = contracts;
    }

    @Transactional
    public PayrollRunResponse execute(UUID actorAccountId, UUID actorBranchId, CreatePayrollRunRequest request) {
        if (runs.existsByYearAndMonth(request.year(), request.month())) {
            throw new PayrollRunAlreadyExistsException(request.year(), request.month());
        }
        var payPeriodStart = LocalDate.of(request.year(), request.month(), 1);
        var run = runs.save(new PayrollRun(request.year(), request.month()));

        var activeContracts = contracts.findAllByStatus(PayrollConstants.ContractStatus.ACTIVE);
        var createdPayslips = activeContracts.stream()
                .map(contract -> buildDraftPayslip(run, contract, payPeriodStart))
                .toList();
        payslips.saveAll(createdPayslips);

        return toResponse(run, createdPayslips);
    }

    private static Payslip buildDraftPayslip(PayrollRun run, EmploymentContract contract, LocalDate payPeriodStart) {
        var hoursWorked = contract.getContractType() == PayrollConstants.ContractType.COLLABORATOR
                ? BigDecimal.ZERO : null;
        var grossPay = PayrollRules.baseGrossPay(contract, payPeriodStart,
                hoursWorked == null ? BigDecimal.ZERO : hoursWorked);
        var allowancesTotal = PayrollRules.totalAllowances(contract.getAllowances());
        var socialEmployee = PayrollRules.socialInsuranceEmployeeShare(grossPay, contract.getContractType());
        var socialEmployer = PayrollRules.socialInsuranceEmployerShare(grossPay, contract.getContractType());
        var inProbation = PayrollRules.isInProbation(contract, payPeriodStart);
        return new Payslip(run, contract.getAccountId(), contract.getContractType(), grossPay, allowancesTotal,
                socialEmployee, socialEmployer, hoursWorked, inProbation);
    }

    private static PayrollRunResponse toResponse(PayrollRun run, List<Payslip> createdPayslips) {
        var totalGross = createdPayslips.stream().map(Payslip::getGrossPay).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PayrollRunResponse(run.getId(), run.getYear(), run.getMonth(), run.getStatus(),
                createdPayslips.size(), totalGross);
    }
}
