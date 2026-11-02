package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayrollRunNotEditableException extends PayrollException {
    public PayrollRunNotEditableException(UUID payrollRunId) {
        super("PAYROLL_RUN_NOT_EDITABLE", HttpStatus.CONFLICT,
                "Kỳ lương này đã gửi duyệt, không sửa được nữa");
    }
}
