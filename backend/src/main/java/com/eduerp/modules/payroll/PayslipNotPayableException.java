package com.eduerp.modules.payroll;

import org.springframework.http.HttpStatus;

/** Phiếu chưa ở trạng thái đánh dấu hoặc huỷ đánh dấu thanh toán được. */
public final class PayslipNotPayableException extends PayrollException {
    public PayslipNotPayableException(String message) {
        super("PAYROLL_PAYSLIP_NOT_PAYABLE", HttpStatus.CONFLICT, message);
    }
}
