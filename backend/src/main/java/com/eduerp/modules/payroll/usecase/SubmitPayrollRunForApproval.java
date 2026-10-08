package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.InvalidContractTermsException;
import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import com.eduerp.modules.payroll.internal.repository.PayslipRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubmitPayrollRunForApproval {

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;

    SubmitPayrollRunForApproval(PayrollRunRepository runs, PayslipRepository payslips) {
        this.runs = runs;
        this.payslips = payslips;
    }

    @Transactional
    public void execute(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.DRAFT) {
            throw new PayrollRunNotEditableException(payrollRunId);
        }
        var runPayslips = payslips.findAllByPayrollRun_Id(payrollRunId);
        var hasUnfilledCollaboratorHours = runPayslips.stream()
                .anyMatch(p -> p.getContractType() == PayrollConstants.ContractType.COLLABORATOR
                        && p.getHoursWorked() != null && p.getHoursWorked().signum() == 0);
        if (hasUnfilledCollaboratorHours) {
            throw new InvalidContractTermsException("Còn phiếu lương CTV chưa nhập giờ dạy");
        }
        run.submitForApproval();
    }
}
