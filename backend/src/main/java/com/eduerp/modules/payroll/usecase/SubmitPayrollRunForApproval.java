package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollPeriodNotEndedException;
import com.eduerp.modules.payroll.PayrollRunEmptyException;
import com.eduerp.modules.payroll.PayrollRunMissingPayTermsException;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.internal.PayrollClock;
import com.eduerp.modules.payroll.internal.PayslipSynchronizer;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Luôn tính lại trước khi gửi: người duyệt phải thấy số mới nhất, kể cả khi điểm danh vừa được sửa sau lần tính
 * trước. Tháng chưa hết thì giờ dạy còn thiếu; còn người đã dạy mà chưa có mức lương thì họ sẽ bị bỏ sót.
 */
@Service
public class SubmitPayrollRunForApproval {

    private final PayrollRunRepository runs;
    private final PayslipSynchronizer synchronizer;
    private final PayrollClock clock;

    SubmitPayrollRunForApproval(PayrollRunRepository runs, PayslipSynchronizer synchronizer, PayrollClock clock) {
        this.runs = runs;
        this.synchronizer = synchronizer;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID payrollRunId, UUID actorAccountId, UUID actorBranchId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.DRAFT) {
            throw new PayrollRunNotEditableException(payrollRunId);
        }
        var periodEnd = run.getPeriod().atEndOfMonth();
        if (!clock.today().isAfter(periodEnd)) {
            throw new PayrollPeriodNotEndedException(periodEnd);
        }
        var unpaidTeachers = synchronizer.missingPayTerms(run.getPeriod()).values().stream()
                .filter(sessionCount -> sessionCount > 0)
                .count();
        if (unpaidTeachers > 0) {
            throw new PayrollRunMissingPayTermsException(unpaidTeachers);
        }
        if (synchronizer.synchronize(run).isEmpty()) {
            throw new PayrollRunEmptyException(payrollRunId);
        }
        run.submitForApproval();
    }
}
