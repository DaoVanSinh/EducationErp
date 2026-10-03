package com.eduerp.modules.payroll.dto;

import com.eduerp.modules.payroll.PayrollConstants;
import java.math.BigDecimal;
import java.util.UUID;

public record PayrollRunResponse(UUID id, int year, int month, PayrollConstants.PayrollRunStatus status,
        int payslipCount, BigDecimal totalGrossPay) {
}
