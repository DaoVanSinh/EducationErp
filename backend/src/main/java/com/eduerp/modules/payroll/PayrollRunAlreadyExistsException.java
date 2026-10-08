package com.eduerp.modules.payroll;

import org.springframework.http.HttpStatus;

/** Review Focus #4: chặn tạo PayrollRun trùng (year, month) bằng lỗi rõ ràng. */
public final class PayrollRunAlreadyExistsException extends PayrollException {
    public PayrollRunAlreadyExistsException(int year, int month) {
        super("PAYROLL_RUN_ALREADY_EXISTS", HttpStatus.CONFLICT, "Kỳ lương " + year + "-" + month + " đã tồn tại");
    }
}
