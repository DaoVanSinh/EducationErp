package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayrollRunNotFoundException extends PayrollException {
    public PayrollRunNotFoundException(UUID payrollRunId) {
        super("PAYROLL_RUN_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy kỳ lương " + payrollRunId);
    }
}
