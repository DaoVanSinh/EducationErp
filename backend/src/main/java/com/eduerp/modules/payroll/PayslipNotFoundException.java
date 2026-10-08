package com.eduerp.modules.payroll;

import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class PayslipNotFoundException extends PayrollException {
    public PayslipNotFoundException(UUID payslipId) {
        super("PAYROLL_PAYSLIP_NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy phiếu lương " + payslipId);
    }
}
