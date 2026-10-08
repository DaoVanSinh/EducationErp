package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.PayrollRunNotPendingApprovalException;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RejectPayrollRun {

    private final PayrollRunRepository runs;

    RejectPayrollRun(PayrollRunRepository runs) {
        this.runs = runs;
    }

    @Transactional
    public void execute(UUID payrollRunId, String reason, UUID actorAccountId, UUID actorBranchId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.PENDING_APPROVAL) {
            throw new PayrollRunNotPendingApprovalException(payrollRunId);
        }
        run.reject(reason);
    }
}
