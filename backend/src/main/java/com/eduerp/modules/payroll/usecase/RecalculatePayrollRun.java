package com.eduerp.modules.payroll.usecase;

import com.eduerp.modules.payroll.PayrollConstants;
import com.eduerp.modules.payroll.PayrollRunNotEditableException;
import com.eduerp.modules.payroll.PayrollRunNotFoundException;
import com.eduerp.modules.payroll.internal.PayslipSynchronizer;
import com.eduerp.modules.payroll.internal.repository.PayrollRunRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc lại điều khoản lương và điểm danh sau khi có sửa đổi. Chỉ kỳ nháp: kỳ đã gửi duyệt giữ nguyên số liệu. */
@Service
public class RecalculatePayrollRun {

    private final PayrollRunRepository runs;
    private final PayslipSynchronizer synchronizer;

    RecalculatePayrollRun(PayrollRunRepository runs, PayslipSynchronizer synchronizer) {
        this.runs = runs;
        this.synchronizer = synchronizer;
    }

    @Transactional
    public void execute(UUID payrollRunId) {
        var run = runs.findById(payrollRunId).orElseThrow(() -> new PayrollRunNotFoundException(payrollRunId));
        if (run.getStatus() != PayrollConstants.PayrollRunStatus.DRAFT) {
            throw new PayrollRunNotEditableException(payrollRunId);
        }
        synchronizer.synchronize(run);
    }
}
