package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollEvents;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.PayrollRunNotPendingApprovalException;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApprovePayrollRun {

    private final PayrollRunRepository runs;
    private final ApplicationEventPublisher events;

    ApprovePayrollRun(PayrollRunRepository runs, ApplicationEventPublisher events) {
        this.runs = runs;
        this.events = events;
    }

    @Transactional
    public void execute(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.PENDING_APPROVAL) {
            throw new PayrollRunNotPendingApprovalException(payrollRunId);
        }
        run.approve();
        events.publishEvent(new PayrollEvents.PayrollRunApproved(payrollRunId, actorAccountId, actorBranchId));
    }
}
