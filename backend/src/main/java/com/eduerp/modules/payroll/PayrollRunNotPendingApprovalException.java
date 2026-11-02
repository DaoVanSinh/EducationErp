package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayrollRunNotPendingApprovalException extends PayrollException {
    public PayrollRunNotPendingApprovalException(UUID payrollRunId) {
        super("PAYROLL_RUN_NOT_PENDING_APPROVAL", HttpStatus.CONFLICT,
                "Kỳ lương này không ở trạng thái chờ duyệt");
    }
}
